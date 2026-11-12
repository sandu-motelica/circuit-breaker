package io.github.sandumotelica.circuitbreaker.observability;

import java.time.Duration;

public record CircuitBreakerStatistics(
        long totalCalls,
        long failedCalls,
        long rejectedCalls,
        Duration averageDuration) {
}
