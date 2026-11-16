# Circuit Breaker System Evaluation

## Scope

This report evaluates the custom circuit breaker in the complete local HTTP flow:

```text
scenario-runner -> demo-protected-client -> demo-unstable-service
```

It checks whether the breaker protects the upstream service during failures and
slow calls, and whether it recovers afterward. The reported latency includes the
runner, two local HTTP hops, Spring MVC processing, the circuit breaker, and the
configured upstream delay. It is therefore a system evaluation, not an isolated
microbenchmark of `circuit-breaker-core`.

## Tested revision and environment

- Tested commit: `74e671a`
- Evaluation date: 2026-11-16
- CPU: 12th Gen Intel Core i5-12450H, 12 logical processors
- Operating system: Windows 11, build 26200, x86-64
- Java: Oracle JDK 21.0.8
- Maven: 3.9.11 through Maven Wrapper
- Network: local loopback; all processes ran on the same machine

The protected client used its checked-in configuration:

| Setting                |                Value |
|------------------------|---------------------:|
| Sliding window         | Count based, 4 calls |
| Minimum calls          |                    4 |
| Failure rate threshold |                  50% |
| Slow call threshold    |                  50% |
| Slow call duration     |             1 second |
| Open duration          |            5 seconds |
| Half-open probe calls  |                    2 |

## Method

Both demo services were built with `./mvnw --batch-mode verify` and started once.
A healthy scenario was run once as warm-up and discarded. Each measured scenario
was then run five times. The scenario runner reset the circuit breaker and the
upstream request counter before every run.

The three basic scenarios used one runner thread to make state transitions easy
to reproduce. The stress scenario used eight threads. No other intentional
traffic was sent to the services during the evaluation.

The table reports the median of the five runs for every field. The complete
per-run data is available in
[`results/custom-implementation-runs.csv`](results/custom-implementation-runs.csv).

## Results

| Scenario            | Threads | Planned | Successful | Upstream failures | Rejected | Reached upstream | Final state |
|---------------------|--------:|--------:|-----------:|------------------:|---------:|-----------------:|-------------|
| Healthy             |       1 |      20 |         20 |                 0 |        0 |               20 | CLOSED      |
| Slow service        |       1 |      16 |         12 |                 0 |        4 |               12 | OPEN        |
| Outage and recovery |       1 |      44 |         18 |                 2 |       24 |               20 | CLOSED      |
| Stress cycles       |       8 |   1,166 |        403 |                 4 |      759 |              407 | CLOSED      |

No run produced a transport error or an unexpected HTTP response. For all 20
runs, the response categories added up exactly to the number of planned calls,
and the upstream request count equaled successful calls plus upstream failures.

### End-to-end latency

| Scenario            | Median average | Median p50 | Median p95 | Median p99 | Median average scheduling drift |
|---------------------|---------------:|-----------:|-----------:|-----------:|--------------------------------:|
| Healthy             |      36.000 ms |      34 ms |      46 ms |      52 ms |                        9.950 ms |
| Slow service        |     400.688 ms |      34 ms |   1,522 ms |   1,522 ms |                        9.000 ms |
| Outage and recovery |      19.477 ms |       6 ms |      45 ms |      54 ms |                        9.636 ms |
| Stress cycles       |      21.865 ms |       3 ms |      34 ms |      73 ms |                        8.864 ms |

The low median latency in failure scenarios is expected: calls rejected by an
open circuit return quickly and never wait for the upstream service. In the slow
scenario, p95 closely follows the configured 1,500 ms upstream delay.

## Interpretation

The healthy scenario passed every request to the upstream service and kept the
circuit closed.

The slow scenario opened the circuit without upstream errors. Four later calls
were rejected, showing that successful but excessively slow responses can still
protect the dependency.

The outage scenario exposed only two failures to callers before the circuit
started rejecting calls. After the open period, successful half-open probes
closed the circuit. The same outcome was observed in all five runs.

The stress scenario exercised two open periods and two recoveries. Of 1,166
planned calls, 759 (65.1%) were rejected before reaching the unstable service.
Only 407 calls reached the upstream service, and the circuit finished closed in
all five runs. This demonstrates load reduction during unhealthy periods and
recovery when the service becomes healthy again.

## Limitations

- All components ran on one development machine over loopback.
- Windows scheduling, JVM activity, and local system load affect latency.
- The scenarios use fixed outcomes and delays; they do not model every production
  network failure.
- Five repetitions are sufficient for a repeatable project evaluation, but they
  are not a substitute for a controlled performance laboratory.

The stress run had a median maximum scheduling drift of 59 ms. One individual
run reached 286 ms, which shows why the raw per-run data and repeated measurements
are retained instead of reporting only the best result.

## Reproduction

Build the repository and start the unstable service and protected client as
described in their module READMEs. Then run, for example:

```sh
java -jar scenario-runner/target/scenario-runner-0.1.0-SNAPSHOT-all.jar \
  scenario-runner/scenarios/stress-cycles.csv \
  scenario-runner/results/stress-cycles 8
```

The runner writes `calls.csv` and `summary.json` to the selected output directory.
Generated result directories are ignored by Git; only the compact measurements
selected for this report are versioned.
