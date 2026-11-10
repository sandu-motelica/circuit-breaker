package com.sm.cb.observability;

import java.time.Duration;

public record CircuitBreakerStatistics(
        long totalCalls,
        long failedCalls,
        long rejectedCalls,
        Duration averageDuration) {
}
