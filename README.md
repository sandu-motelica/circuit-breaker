# Circuit Breaker

An in-process circuit breaker for Java 21, built to protect calls to an
unreliable dependency. It records failures and slow responses, stops sending
calls when configured limits are reached, and probes the dependency before
resuming traffic. The repository pairs the library with an HTTP demo and a
measured comparison against Resilience4j.

## Library at a glance

- `COUNT_BASED` keeps the last *N* calls; `TIME_BASED` keeps calls from the last
  *N* seconds. Both evaluate failure and slow-call rates after a configurable
  minimum number of calls.
- Synchronous `Supplier` and asynchronous `CompletableFuture` execution, with
  configurable recorded and ignored exception types.
- Bounded recovery probes in `HALF_OPEN`; manual `FORCED_OPEN`, `DISABLED`, and
  `METRICS_ONLY` states for operational control.
- Events for outcomes and state changes, cumulative call statistics, window
  rates, and a thread-safe registry for named breakers.
- An injectable `Clock` for deterministic tests of time windows, slow calls,
  and recovery delays.

## How the breaker handles a call

1. In `CLOSED`, the call runs and its outcome enters a count ring or a set of
   per-second buckets. Once enough calls have been observed, a failure or
   slow-call rate at the configured threshold moves the breaker to `OPEN`.
2. In `OPEN`, the supplier is not invoked. After the wait period, the next call
   moves the breaker to `HALF_OPEN`.
3. In `HALF_OPEN`, a limited number of concurrent probes are admitted. Their
   results either close the circuit or open it again.

For concurrent callers, admission reads a published state snapshot; window
updates that can change a decision remain ordered. Compare-and-set counters
limit `HALF_OPEN` probes, while `LongAdder` tracks cumulative statistics.

## Example

```java
CircuitBreakerConfig config = CircuitBreakerConfig.builder()
        .slidingWindowSize(10)
        .minimumCalls(5)
        .failureRateThreshold(0.5f)
        .slowCallDurationThreshold(Duration.ofMillis(500))
        .slowCallRateThreshold(0.5f)
        .build();

CircuitBreaker breaker = new CircuitBreaker(config);
String response = breaker.execute(() -> remoteService.call());
```

Import `CircuitBreaker` and `CircuitBreakerConfig` from
`io.github.sandumotelica.circuitbreaker`, plus `java.time.Duration`. The
count-based window is the default; use
`slidingWindowType(SlidingWindowType.TIME_BASED)` to configure a time window.
An open circuit throws `CallNotPermittedException` before invoking the supplier.

## Modules

| Module | Purpose |
| --- | --- |
| [`circuit-breaker-core`](circuit-breaker-core) | Library, state machine, windows, registry, and concurrency tests |
| [`demo-unstable-service`](demo-unstable-service) | Spring Boot upstream with chosen outcomes and delays |
| [`demo-protected-client`](demo-protected-client) | Two HTTP routes using the same upstream: custom breaker and Resilience4j |
| [`scenario-runner`](scenario-runner) | Repeatable request plans and per-call CSV/JSON output |
| [`circuit-breaker-benchmarks`](circuit-breaker-benchmarks) | JMH throughput, latency, and allocation measurements |

## Build and explore

Build and test the project with **JDK 21** and the Maven wrapper:

```sh
./mvnw verify
```

On Windows, use `.\mvnw.cmd verify`. The build runs state, concurrency, and HTTP
integration tests; JMH is run separately.

### HTTP demo

Start the upstream service and protected client in two terminals from the
repository root:

```sh
java -jar demo-unstable-service/target/demo-unstable-service-0.1.0-SNAPSHOT.jar
java -jar demo-protected-client/target/demo-protected-client-0.1.0-SNAPSHOT.jar
```

Both routes use the same thresholds and call the same upstream service on port
8081. Choose an outcome to see how each breaker responds:

```sh
curl "http://localhost:8080/api/payments/payment-123?mode=SUCCESS"
curl "http://localhost:8080/api/payments/resilience4j/payment-123?mode=FAILURE"
```

The upstream also accepts a `delayMs` parameter, so failures and slow calls
can be reproduced independently. The [client README](demo-protected-client/README.md)
lists status and reset endpoints and explains how to switch both routes to
time-based windows.

### Repeatable scenarios

Run the same HTTP scenario through both routes:

```sh
java -jar scenario-runner/target/scenario-runner-0.1.0-SNAPSHOT-all.jar \
  scenario-runner/scenarios/outage-recovery.csv scenario-runner/results/compare \
  1 http://localhost:8080 http://localhost:8081 both
```

The runner resets each breaker between routes and saves per-request timings,
response counts, and a comparison summary under `scenario-runner/results/`.
It also checks how many calls actually reached the upstream service, separating
rejections from dependency failures. Other plans cover healthy traffic, slow
responses, failure waves, and recovery. See the
[scenario runner README](scenario-runner/README.md).

### JMH benchmarks

Run the in-process JMH suite separately from the HTTP services:

```sh
java -jar circuit-breaker-benchmarks/target/circuit-breaker-benchmarks-0.1.0-SNAPSHOT-all.jar \
  -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc
```

It measures success, failures, rejections, metric reads, and concurrent mixed
traffic for both window types. See the
[benchmark instructions](circuit-breaker-benchmarks/README.md) for options and
the exact command used for the recorded results.

## What the measurements show

Selected results from the local JMH throughput run (`M/s` = million calls per
second). Allocation uses JMH's GC profiler; `~0` means less than 0.1 B/op.

| Workload | Custom throughput | Resilience4j throughput | Custom B/op | Resilience4j B/op |
| --- | ---: | ---: | ---: | ---: |
| Success, `COUNT_BASED`, 1 thread | 20.01 M/s | 9.84 M/s | ~0 | 24 |
| Success, `COUNT_BASED`, 4 threads | 39.98 M/s | 7.69 M/s | ~0 | ~24 |
| Success, `TIME_BASED`, 1 thread | 13.59 M/s | 9.20 M/s | ~0 | ~0 |
| Success, `TIME_BASED`, 4 threads | 6.06 M/s | 7.58 M/s | ~0 | ~1 |
| Failure, `TIME_BASED`, 1 thread | 5.89 M/s | 4.91 M/s | 64 | ~15 |
| Rejected in `OPEN`, `TIME_BASED`, 1 thread | 0.76 M/s | 0.55 M/s | 752 | 1,288 |
| Mixed traffic, `TIME_BASED`, 4 threads | 2,477/s | 2,472/s | ~16 | ~52 |

- **Success-only results favor the custom count window.** In this benchmark it
  is already full of consecutive successes. Another ordinary success leaves
  that ring unchanged, avoiding work and contention; failures or slow calls
  remove that advantage. It is an optimistic steady state.
- **The time window changes the concurrency result.** Custom is faster with one
  thread, while Resilience4j is faster with four in this run. The custom window
  serializes bucket updates, which likely contributes to contention. The
  two-fork check showed the same ordering, but throughput varied between runs.
- **Mixed traffic is close in throughput.** The workload includes 75% immediate
  successes, 15% failures, and 10% delayed successes. Its requested 2 ms delay
  produced roughly 16 ms sampled p95 on this Windows machine, so scheduling
  dominates and the small throughput gap is within measurement variation.
- **Error paths have different costs.** The custom failure case allocates more,
  while its OPEN rejection allocates less in this run. The supplier failure uses
  a prebuilt exception; rejections create exceptions, so these rows measure
  different work from the success path.

Earlier paired HTTP scenarios produced matching success, failure, and rejection
counts for both breakers. They exercise the complete request path; their latency
differences do not establish an end-to-end speed advantage. These JMH results
come from short local runs and are not production performance guarantees.

See the [JMH report](docs/evaluation/jmh-benchmarks.md),
[raw results](docs/evaluation/benchmark-results/results.json), and earlier
[HTTP comparison](docs/evaluation/resilience4j-comparison.md) for methods and
full results.

## Scope and limits

- State is local to one process; the project does not coordinate breakers
  across instances or persist their state.
- Recovery is call driven: no background timer moves an `OPEN` breaker to
  `HALF_OPEN` when its wait period expires.
- The benchmark suite isolates selected workloads. It does not replace load
  testing with a real dependency and production traffic.

## License

This project is available under the [MIT License](LICENSE).
