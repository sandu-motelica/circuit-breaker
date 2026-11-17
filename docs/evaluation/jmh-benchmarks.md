# In-process circuit breaker benchmarks

## Scope

The new `circuit-breaker-benchmarks` module compares the custom breaker with
Resilience4j 2.4.0 using JMH 1.37. It measures a direct supplier call, successful
calls, recorded failures, OPEN rejections, failure-rate reads, and successful
calls through a breaker shared by four threads. Success runs with both count
and time windows, so those cases include the corresponding window update.
There is no HTTP in the measured path.

The environment, JMH options, and raw files are in
[`benchmark-results`](../../benchmark-results/environment.md). JMH ran two
one-second warmup iterations and three one-second measurement iterations in
one fork. The values below are exploratory measurements from that single local
run, not performance thresholds for CI.

## Throughput and allocations

Throughput is millions of operations per second. Allocation is bytes per
operation from JMH's GC profiler. The direct supplier baseline measured
approximately **3,099 million operations/s** and effectively zero allocation;
its tiny work makes it a harness reference, not an estimate of real application
throughput.

| Operation | Window | Custom M ops/s | Resilience4j M ops/s | Custom B/op | Resilience4j B/op |
| --- | --- | ---: | ---: | ---: | ---: |
| Successful call | Count | 12.30 | 15.06 | 24 | 24 |
| Successful call | Time | 11.23 | 13.15 | 24 | ~0 |
| Failed call | Count | 7.98 | 7.79 | 88 | 24 |
| Failed call | Time | 8.47 | 7.39 | 88 | 40 |
| Rejected call in OPEN | Count | 0.92 | 0.90 | 752 | 1,264 |
| Rejected call in OPEN | Time | 0.74 | 0.90 | 752 | 1,264 |
| Read failure rate | Count | 37.49 | 67.58 | ~0 | ~0 |
| Read failure rate | Time | 28.02 | 41.87 | ~0 | ~0 |
| Shared successful call, 4 threads | Count | 4.37 | 7.33 | 24 | ~25 |
| Shared successful call, 4 threads | Time | 4.85 | 7.78 | 24 | ~1 |

In this run, Resilience4j had higher throughput for successful calls, rate
reads, and the four-thread shared breaker. Failure throughput was close with a
count window; the custom path was higher with a time window. OPEN rejection
throughput was close with a count window and varied by window type. Allocation
differences were clearest on rejections, where both implementations create an
exception: about 752 B/op for custom and 1,264 B/op for Resilience4j.
The [successful-call chart](../../benchmark-results/charts/success-throughput.md)
shows the two window variants at a glance.

## Sampled latency

JMH also measured average sampled latency and percentiles. Selected count-window
results, in nanoseconds per operation, were:

| Operation | Custom mean / p95 | Resilience4j mean / p95 |
| --- | ---: | ---: |
| Successful call | 223 / 200 | 167 / 100 |
| Failed call | 403 / 300 | 365 / 300 |
| Rejected call in OPEN | 1,962 / 2,200 | 2,567 / 2,800 |
| Shared successful call, 4 threads | 1,517 / 2,400 | 1,017 / 200 |

Some means exceed p95 because rare long-tail samples pull the mean upward.
The sample timer has coarse resolution for operations near 100 ns; its baseline
mean was about 208 ns despite the very high baseline throughput. The full
latency distributions are in `results.json` and `results.csv` and should not be
read as precise sub-microsecond costs.

## What the numbers include

The successful operation returns a volatile value through the same supplier in
both implementations. Failed calls reuse one preconstructed exception and run
in METRICS_ONLY so the breaker continues recording failures without changing
state. Rejected calls use OPEN with a one-day wait, so they stay rejected during
the run. The four-thread case shares one breaker instance within each fork.
These choices isolate steady states; they do not measure exception creation for
the failed supplier, transition frequency, listeners, or asynchronous completion.
The success breaker is primed with 1,024 successful calls during setup, so the
failure-rate read compares a populated window in both implementations.

The implementations use different clocks and internal metric structures. One
fork and short iterations make small differences especially sensitive to JIT,
GC, and system activity. The stronger signals here are the direction of the
four-thread throughput gap and the rejection allocation gap. Repeat with more
forks and longer iterations before making a performance claim beyond this
machine. The [HTTP comparison](resilience4j-comparison.md) remains the measure
of complete application behavior.
