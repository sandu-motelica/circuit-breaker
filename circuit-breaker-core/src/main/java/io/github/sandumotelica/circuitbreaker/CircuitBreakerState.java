package io.github.sandumotelica.circuitbreaker;

/**
 * Operational and administrative states of a {@link CircuitBreaker}.
 */
public enum CircuitBreakerState {
    /** Calls are permitted and their outcomes may open the circuit. */
    CLOSED,
    /** Calls are rejected until the configured open-state duration elapses. */
    OPEN,
    /** A limited set of probe calls determines whether the dependency has recovered. */
    HALF_OPEN,
    /** All calls are rejected until a manual transition occurs. */
    FORCED_OPEN,
    /** All calls pass through without result tracking or automatic transitions. */
    DISABLED,
    /** All calls pass through and are measured without automatic transitions. */
    METRICS_ONLY;

    boolean recordsResults() {
        return this != DISABLED;
    }

    boolean resetsSlidingWindowOnManualTransition() {
        return switch (this) {
            case CLOSED, FORCED_OPEN, DISABLED, METRICS_ONLY -> true;
            case OPEN, HALF_OPEN -> false;
        };
    }
}
