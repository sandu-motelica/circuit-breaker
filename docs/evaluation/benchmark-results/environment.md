# JMH run environment

- Date: 2026-11-17
- Branch: `benchmark/jmh-core`
- Base commit: `94b2942` (benchmark module changes were in the working tree)
- Processor: 12th Gen Intel Core i5-12450H, 12 logical processors
- Operating system: Windows 11, build 26200, x86-64
- Java: Oracle JDK 21.0.8
- JMH: 1.37
- Resilience4j: 2.4.0
- Maven: 3.9.11
- Warmup: 2 iterations × 1 second
- Measurement: 3 iterations × 1 second
- Forks: 1
- Threads: 1 for ordinary cases, 4 for `concurrentSuccessfulCall`
- Modes: throughput and sampled latency
- GC profiler: enabled (`-prof gc`)
- Window parameters: count-based 1,024 calls; time-based 10 seconds
- Minimum calls: 1,024; thresholds: 100%; slow-call duration and OPEN duration: 1 day
- The success breaker was filled with 1,024 successful calls during setup, outside measurement

The command used from the repository root was:

```sh
java -jar circuit-breaker-benchmarks/target/circuit-breaker-benchmarks-0.1.0-SNAPSHOT-all.jar \
  -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc \
  -rf json -rff docs/evaluation/benchmark-results/results.json
```

This is a local exploratory run. One fork and short iterations limit how much
can be inferred from small differences. `results.csv` is a flat export of the
same JSON results; it does not represent a second benchmark run.
