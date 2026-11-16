package io.github.sandumotelica.circuitbreaker.observability;

import java.time.Duration;

/**
 * Immutable snapshot of cumulative circuit-breaker statistics.
 *
 * <p>Total calls include successful calls, recorded failures, and calls with
 * ignored exceptions. Rejected calls are counted separately. Calls made while
 * the breaker is disabled are not measured.</p>
 *
 * @param totalCalls number of measured calls that completed or failed
 * @param failedCalls number of calls classified as failures
 * @param rejectedCalls number of calls rejected before invocation
 * @param averageDuration average duration of measured calls
 */
public record CircuitBreakerStatistics(
        long totalCalls,
        long failedCalls,
        long rejectedCalls,
        Duration averageDuration) {
}
