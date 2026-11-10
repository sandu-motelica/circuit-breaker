package com.sm.cb;

import com.sm.cb.observability.CircuitBreakerStatistics;

import java.time.Duration;

final class CircuitBreakerStatisticsCollector {
    private long totalCalls;
    private long failedCalls;
    private long rejectedCalls;
    private long totalDurationNanos;

    void recordCall(Duration duration, boolean failure) {
        totalCalls++;
        if (failure) {
            failedCalls++;
        }
        totalDurationNanos += duration.toNanos();
    }

    void recordRejectedCall() {
        rejectedCalls++;
    }

    CircuitBreakerStatistics snapshot() {
        Duration averageDuration = totalCalls == 0
                ? Duration.ZERO
                : Duration.ofNanos(totalDurationNanos / totalCalls);
        return new CircuitBreakerStatistics(totalCalls, failedCalls, rejectedCalls, averageDuration);
    }
}
