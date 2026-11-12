package io.github.sandumotelica.circuitbreaker.internal;

import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;

import java.time.Duration;

public final class CircuitBreakerStatisticsCollector {
    private long totalCalls;
    private long failedCalls;
    private long rejectedCalls;
    private long totalDurationNanos;

    public void recordCall(Duration duration, boolean failure) {
        totalCalls++;
        if (failure) {
            failedCalls++;
        }
        totalDurationNanos += duration.toNanos();
    }

    public void recordRejectedCall() {
        rejectedCalls++;
    }

    public void reset() {
        totalCalls = 0;
        failedCalls = 0;
        rejectedCalls = 0;
        totalDurationNanos = 0;
    }

    public CircuitBreakerStatistics snapshot() {
        Duration averageDuration = totalCalls == 0
                ? Duration.ZERO
                : Duration.ofNanos(totalDurationNanos / totalCalls);
        return new CircuitBreakerStatistics(totalCalls, failedCalls, rejectedCalls, averageDuration);
    }
}
