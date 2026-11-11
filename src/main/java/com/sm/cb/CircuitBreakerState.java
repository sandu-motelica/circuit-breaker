package com.sm.cb;

public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN,
    FORCED_OPEN,
    DISABLED,
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
