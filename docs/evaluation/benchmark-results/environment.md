# JMH run environment

- Date: 2026-11-17
- Branch: `performance/reduce-breaker-contention`
- Base commit: `2e65157` (performance changes were in the working tree)
- Processor: 12th Gen Intel Core i5-12450H, 12 logical processors
- Operating system: Windows 11, build 26200, x86-64
- Java: Oracle JDK 21.0.8
- JMH: 1.37
- Resilience4j: 2.4.0
- Maven: 3.9.11
- Warmup: 2 iterations × 1 second
- Measurement: 3 iterations × 1 second
- Forks: 1
- Threads: 1 for ordinary cases, 4 for `concurrentSuccessfulCall` and `concurrentMixedTraffic`
- Modes: throughput and sampled latency
- GC profiler: enabled (`-prof gc`)
- Window parameters: count-based 1,024 calls; time-based 10 seconds
- Minimum calls: 1,024; thresholds: 100%; slow-call duration and OPEN duration: 1 day
- The success breaker was filled with 1,024 successful calls during setup, outside measurement
- Mixed traffic: 75% immediate successes, 15% prebuilt failures, and 10% delayed successes (`parkNanos(2 ms)`), four threads, METRICS_ONLY, 1 ms slow-call threshold

The command used from the repository root was:

```sh
java -jar circuit-breaker-benchmarks/target/circuit-breaker-benchmarks-0.1.0-SNAPSHOT-all.jar \
  -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc \
  -rf json -rff docs/evaluation/benchmark-results/results.json
```

This is a local exploratory run. One fork and short iterations limit how much
can be inferred from small differences. `results.csv` is a flat export of the
same JSON results; it does not represent a second benchmark run.

The time-window success cases were also checked with two forks, three
one-second warmup iterations, and five one-second measurement iterations:

```sh
java -jar circuit-breaker-benchmarks/target/circuit-breaker-benchmarks-0.1.0-SNAPSHOT-all.jar \
  'CircuitBreakerBenchmark.(successfulCall|concurrentSuccessfulCall)' -bm thrpt \
  -p implementation=custom,resilience4j -p window=time \
  -wi 3 -i 5 -w 1s -r 1s -f 2 -prof gc
```

The local raw output is `circuit-breaker-benchmarks/target/time-confirmation.json`
(ignored by Git). Custom allocation was 0.001 B/op with one thread and 0.002
B/op with four. Resilience4j measured 0.065 and 21.008 B/op respectively;
its four-thread allocation differed from the one-fork run, so that estimate is
not stable across these short measurements.
