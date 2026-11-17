# Custom circuit breaker vs Resilience4j

## Result

Across the four supplied scenarios, **both implementations returned the same
number of successes, upstream failures, and rejected calls in every run**. Their
end-to-end HTTP latencies were also close. These measurements do not show a
reliable performance advantage for either circuit breaker.

## How the comparison was run

On 2026-11-17, both demo services ran on the same Windows machine with Oracle
JDK 21.0.8. Each scenario ran four times through each API: **32 runs** in total.
The order within each scenario was custom → Resilience4j, Resilience4j → custom,
Resilience4j → custom, custom → Resilience4j. The runner reset the upstream
counter and selected circuit before every run.

Both routes shared the same upstream HTTP client and breaker settings: a
four-call count window, four minimum calls, 50% failure and slow-call thresholds,
a one-second slow-call threshold, five seconds in OPEN, and two HALF_OPEN probes.
The first three scenarios used one runner thread; `stress-cycles` used eight.
The services stayed running throughout the experiment. No warm-up run was
discarded.

## Behavior across runs

The table shows the result **of each individual run**. For every row, both
implementations produced those counts in all four repetitions. The observed
variation in these counts and in final state was therefore zero.

| Scenario | Requests | Success | Upstream failure | Rejected | Upstream received | Final state |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `healthy` | 20 | 20 | 0 | 0 | 20 | CLOSED |
| `outage-recovery` | 44 | 18 | 2 | 24 | 20 | CLOSED |
| `slow-service` | 16 | 12 | 0 | 4 | 12 | OPEN |
| `stress-cycles` | 1,166 | 403 | 4 | 759 | 407 | CLOSED |

There were no unexpected responses or transport errors. The stress scenario
covered slow-call opening, failure opening, failed HALF_OPEN probes, and recovery.
The matching results apply to these scenarios and settings; they do not prove
identical behavior for every workload.

The table uses HTTP responses observed by the runner and the upstream request
counter. The breakers' own status fields cannot be subtracted directly: the
custom breaker reports fractional rates and cumulative counts, while
Resilience4j reports percentage rates and active-window counts. Its
`notPermittedCalls` metric covers the current OPEN state
([Resilience4j Metrics interface](https://github.com/resilience4j/resilience4j/blob/master/resilience4j-circuitbreaker/src/main/java/io/github/resilience4j/circuitbreaker/CircuitBreaker.java)).

## End-to-end latency

Times are in milliseconds. Each value is the **mean of four per-run averages ±
sample standard deviation between runs**. The paired difference is Resilience4j
minus custom, averaged over the four matching repetitions. Negative values favor
Resilience4j for this HTTP measurement.

| Scenario | Custom mean ± SD | Resilience4j mean ± SD | Paired difference |
| --- | ---: | ---: | ---: |
| `healthy` | 36.39 ± 3.94 | 34.05 ± 0.19 | −2.34 ms |
| `outage-recovery` | 18.41 ± 0.32 | 18.01 ± 0.08 | −0.41 ms |
| `slow-service` | 398.25 ± 0.49 | 398.56 ± 0.41 | +0.31 ms |
| `stress-cycles` | 21.36 ± 0.17 | 21.34 ± 0.18 | −0.02 ms |

The first `healthy` run measured 42.25 ms for custom and 34.30 ms for
Resilience4j. In the other three pairs, the mean difference was −0.47 ms. That
first-run effect is the main reason the `healthy` row has a larger difference
and spread.

In `stress-cycles`, admitted successful calls averaged 58.52 ms for custom and
58.56 ms for Resilience4j; rejected calls averaged 1.57 and 1.51 ms. The runner
records individual durations in whole milliseconds. These figures include HTTP,
Spring MVC, scheduling, and upstream delay where applicable. They measure the
complete local request path, not isolated breaker overhead; differences of a
few hundredths of a millisecond do not justify a speed claim.

## Data and reproduction

The per-call CSVs, per-run summaries, and `aggregate.json` are in
`scenario-runner/results/repeated-2026-11-17/` in this workspace. Results are
ignored by Git. To repeat the experiment, build and start the services as shown
in the [scenario runner README](../../scenario-runner/README.md), then run each
scenario four times per target (`custom`, `resilience4j`) in the order above.
Use eight threads for `stress-cycles` and one for the others. Each runner
invocation resets its selected breaker and the upstream request counter.
