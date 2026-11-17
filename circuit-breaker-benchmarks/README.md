# Circuit breaker microbenchmarks

This module measures the in-process cost of the custom breaker and Resilience4j
2.4.0 with JMH 1.37. It uses no HTTP. Build and run the shaded JAR from the
repository root:

```sh
./mvnw -pl circuit-breaker-benchmarks -am verify
java -jar circuit-breaker-benchmarks/target/circuit-breaker-benchmarks-0.1.0-SNAPSHOT-all.jar \
  -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc \
  -rf json -rff docs/evaluation/benchmark-results/results.json
```

The suite covers a direct supplier call, successful calls with count-based and
time-based windows, failed calls, rejected calls in OPEN, failure-rate reads,
and successful calls through a shared breaker with four threads. The successful
call also measures window updates. The two implementations receive the same
supplier, window size, threshold settings, and long OPEN duration. Failed calls
use METRICS_ONLY to keep recording failures without a state change during the
measurement; both implementations reuse one preconstructed exception. The
four-thread method uses a single shared breaker per fork.
The success breaker receives 1,024 setup calls before measurement, so the
failure-rate read has enough history in either implementation.

JMH runs throughput and sampled latency for each case. Sampled latency includes
percentiles; `-prof gc` records allocation per operation. Throughput is reported
by JMH in operations per nanosecond because the annotation uses nanoseconds;
multiply by 1,000,000,000 for operations per second. Run the JAR rather than a
JUnit test or an IDE run configuration, as recommended by the
[JMH project](https://github.com/openjdk/jmh#usage).

The measured [environment and results](../docs/evaluation/benchmark-results/environment.md) are
recorded separately. The `results.json` and `results.csv` files are generated
outputs. CI compiles the module through the root Maven build but does not run
the JMH suite. Listener dispatch and asynchronous execution are outside this
first suite; their setup and completion semantics need separate, carefully
matched experiments.
