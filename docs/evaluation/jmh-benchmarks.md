# In-process circuit breaker benchmarks

## Scope

The `circuit-breaker-benchmarks` module compares the custom breaker with
Resilience4j 2.4.0 using JMH 1.37. It measures a direct supplier call, successful
calls, recorded failures, OPEN rejections, failure-rate reads, and successful
calls and mixed traffic through a breaker shared by four threads. Success runs
with both count and time windows, so those cases include the corresponding
window update.
There is no HTTP in the measured path.

The environment, JMH options, and raw files are in
[`benchmark-results`](benchmark-results/environment.md). JMH ran two
one-second warmup iterations and three one-second measurement iterations in
one fork. The values below are exploratory measurements from that single local
run, not performance thresholds for CI.

## Throughput and allocations

Throughput is millions of operations per second. Allocation is bytes per
operation from JMH's GC profiler. The direct supplier baseline measured
approximately **1,908 million operations/s** and effectively zero allocation;
its tiny work makes it a harness reference, not an estimate of real application
throughput.

| Operation | Window | Custom M ops/s | Resilience4j M ops/s | Custom B/op | Resilience4j B/op |
| --- | --- | ---: | ---: | ---: | ---: |
| Successful call | Count | 20.01 | 9.84 | ~0 | 24 |
| Successful call | Time | 13.59 | 9.20 | ~0 | ~0 |
| Failed call | Count | 5.79 | 5.32 | 64 | 24 |
| Failed call | Time | 5.89 | 4.91 | 64 | ~15 |
| Rejected call in OPEN | Count | 0.74 | 0.56 | 752 | 1,264 |
| Rejected call in OPEN | Time | 0.76 | 0.55 | 752 | 1,288 |
| Read failure rate | Count | 62.54 | 57.02 | ~0 | ~0 |
| Read failure rate | Time | 40.97 | 28.68 | ~0 | ~0 |
| Shared successful call, 4 threads | Count | 39.98 | 7.69 | ~0 | ~24 |
| Shared successful call, 4 threads | Time | 6.06 | 7.58 | ~0 | ~1 |

The large count-window success advantage is an optimistic steady-state case:
the window is already full and contains only ordinary successes, so another
success leaves its ring unchanged. A failure or slow call clears that fast-path
condition. The time window still synchronizes bucket updates, and Resilience4j
leads its four-thread success case in this run. The custom success path avoids
per-call allocation in these JMH measurements. Rejections allocate about
752 B/op for custom and at least 1,264 B/op for Resilience4j.

A two-fork time-window check measured 9.81 ± 1.76 versus 7.62 ± 0.15 million
ops/s with one thread, and 5.44 ± 0.47 versus 6.68 ± 0.93 million ops/s with
four threads (custom versus Resilience4j). Custom allocation was
0.001–0.002 B/op. Throughput varied between runs, so these measurements do
not establish a fixed speedup factor.

The [successful-call chart](benchmark-results/charts/success-throughput.md)
shows the two window variants at a glance.

## Mixed traffic

`concurrentMixedTraffic` shares one breaker among four threads. Each thread
repeats 20 calls: 15 immediate successes, three failures using a prebuilt
exception, and two successful calls with a requested 2 ms delay. Both breakers
run in METRICS_ONLY with a 1 ms slow-call threshold, so the mix continues
without OPEN rejections. This exercises window updates under concurrent mixed
outcomes; the slow supplier and operating-system timer dominate elapsed time.
The sampled p95 was about 16 ms despite the requested 2 ms delay, so this
machine's timer resolution materially affects the throughput result.

| Window | Custom ops/s | Resilience4j ops/s | Custom B/op | Resilience4j B/op |
| --- | ---: | ---: | ---: | ---: |
| Count | 2,490 | 2,475 | ~16 | ~74 |
| Time | 2,477 | 2,472 | ~16 | ~52 |

These one-fork results show near-equal throughput for this delayed workload.
The JMH 99.9% error estimates range from roughly 310 to 633 ops/s, far larger
than the observed gaps. The allocation estimates vary by implementation and
benchmark mode; the table uses throughput-mode measurements only.

## Sampled latency

JMH also measured average sampled latency and percentiles. Selected count-window
results, in nanoseconds per operation, were:

| Operation | Custom mean / p95 | Resilience4j mean / p95 |
| --- | ---: | ---: |
| Successful call | 68 / 100 | 106 / 100 |
| Failed call | 156 / 200 | 148 / 200 |
| Rejected call in OPEN | 896 / 900 | 1,218 / 1,200 |
| Shared successful call, 4 threads, count | 95 / 200 | 762 / 200 |

Some means exceed p95 because rare long-tail samples pull the mean upward.
The sample timer has coarse resolution for operations near 100 ns; its baseline
mean was about 28 ns despite the very high baseline throughput. The full
latency distributions are in `results.json` and `results.csv` and should not be
read as precise sub-microsecond costs.

## What the numbers include

The successful operation returns a volatile value through the same supplier in
both implementations. Failed calls reuse one preconstructed exception and run
in METRICS_ONLY so the breaker continues recording failures without changing
state. Rejected calls use OPEN with a one-day wait, so they stay rejected during
the run. The four-thread cases share one breaker instance within each fork.
These choices isolate steady states; they do not measure exception creation for
the failed supplier, transition frequency, listeners, or asynchronous completion.
The success breaker is primed with 1,024 successful calls during setup, so the
failure-rate read compares a populated window in both implementations.

The implementations use different clocks and internal metric structures. One
fork and short iterations make small differences especially sensitive to JIT,
GC, and system activity. The count-window success result applies to a full,
all-success window. The mixed result includes slow-supplier delay, so its
near-equal throughput does not resolve tiny differences in breaker overhead.

The rejection allocation gap is also visible. Repeat with longer iterations
before making a throughput claim beyond this machine. The
[HTTP comparison](resilience4j-comparison.md) records earlier end-to-end runs.
