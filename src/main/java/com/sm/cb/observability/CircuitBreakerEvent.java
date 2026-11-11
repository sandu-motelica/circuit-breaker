package com.sm.cb.observability;

import com.sm.cb.CircuitBreakerState;

import java.time.Duration;
import java.time.Instant;

public sealed interface CircuitBreakerEvent {
    Instant timestamp();

    record StateTransition(
            Instant timestamp,
            CircuitBreakerState from,
            CircuitBreakerState to) implements CircuitBreakerEvent {
    }

    record CallSucceeded(
            Instant timestamp,
            Duration duration) implements CircuitBreakerEvent {
    }

    record CallFailed(
            Instant timestamp,
            Duration duration,
            Throwable exception) implements CircuitBreakerEvent {
    }

    record CallIgnored(
            Instant timestamp,
            Duration duration,
            Throwable exception) implements CircuitBreakerEvent {
    }

    record SlowCall(
            Instant timestamp,
            Duration duration,
            CallOutcome outcome) implements CircuitBreakerEvent {
    }

    record CallRejected(
            Instant timestamp,
            CircuitBreakerState state,
            String reason) implements CircuitBreakerEvent {
    }

    enum CallOutcome {
        SUCCESS, FAILURE
    }
}
