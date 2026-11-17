package io.github.sandumotelica.circuitbreaker.internal;

import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

public final class CircuitBreakerStatisticsCollector {
    private final LongAdder totalCalls = new LongAdder();
    private final LongAdder failedCalls = new LongAdder();
    private final LongAdder rejectedCalls = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();

    public void recordCall(Duration duration, boolean failure) {
        totalCalls.increment();
        if (failure) {
            failedCalls.increment();
        }
        totalDurationNanos.add(duration.toNanos());
    }

    public void recordRejectedCall() {
        rejectedCalls.increment();
    }

    public CircuitBreakerStatistics snapshot() {
        long calls = totalCalls.sum();
        Duration averageDuration = calls == 0
                ? Duration.ZERO
                : Duration.ofNanos(totalDurationNanos.sum() / calls);
        return new CircuitBreakerStatistics(calls, failedCalls.sum(), rejectedCalls.sum(), averageDuration);
    }
}
