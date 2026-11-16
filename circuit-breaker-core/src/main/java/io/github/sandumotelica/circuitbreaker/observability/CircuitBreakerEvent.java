package io.github.sandumotelica.circuitbreaker.observability;

import io.github.sandumotelica.circuitbreaker.CircuitBreakerState;

import java.time.Duration;
import java.time.Instant;

/**
 * Event emitted by a circuit breaker when a call is completed or rejected, or
 * when the breaker changes state.
 */
public sealed interface CircuitBreakerEvent {
    /**
     * Returns when the event was created.
     *
     * @return event timestamp
     */
    Instant timestamp();

    /**
     * Reports a circuit-breaker state change.
     *
     * @param timestamp time of the transition
     * @param from previous state
     * @param to new state
     */
    record StateTransition(
            Instant timestamp,
            CircuitBreakerState from,
            CircuitBreakerState to) implements CircuitBreakerEvent {
    }

    /**
     * Reports a successfully completed call.
     *
     * @param timestamp time at which the event was created
     * @param duration call duration
     */
    record CallSucceeded(
            Instant timestamp,
            Duration duration) implements CircuitBreakerEvent {
    }

    /**
     * Reports a call whose exception was recorded as a failure.
     *
     * @param timestamp time at which the event was created
     * @param duration call duration
     * @param exception recorded exception
     */
    record CallFailed(
            Instant timestamp,
            Duration duration,
            Throwable exception) implements CircuitBreakerEvent {
    }

    /**
     * Reports a call whose exception was ignored by failure-rate evaluation.
     *
     * @param timestamp time at which the event was created
     * @param duration call duration
     * @param exception ignored exception
     */
    record CallIgnored(
            Instant timestamp,
            Duration duration,
            Throwable exception) implements CircuitBreakerEvent {
    }

    /**
     * Reports a call that met or exceeded the configured slow-call duration.
     *
     * @param timestamp time at which the event was created
     * @param duration call duration
     * @param outcome whether the slow call succeeded or failed
     */
    record SlowCall(
            Instant timestamp,
            Duration duration,
            CallOutcome outcome) implements CircuitBreakerEvent {
    }

    /**
     * Reports a call rejected before its operation was invoked.
     *
     * @param timestamp time at which the event was created
     * @param state state that rejected the call
     * @param reason rejection reason
     */
    record CallRejected(
            Instant timestamp,
            CircuitBreakerState state,
            String reason) implements CircuitBreakerEvent {
    }

    /**
     * Outcome associated with a {@link SlowCall} event.
     */
    enum CallOutcome {
        /** The slow call completed successfully. */
        SUCCESS,
        /** The slow call completed with a recorded failure. */
        FAILURE
    }
}
