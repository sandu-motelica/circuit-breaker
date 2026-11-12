package io.github.sandumotelica.circuitbreaker.internal;

import io.github.sandumotelica.circuitbreaker.CircuitBreakerState;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEvent;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEventListener;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultCircuitBreakerEventPublisher implements CircuitBreakerEventPublisher {
    private final Clock clock;
    private final CopyOnWriteArrayList<CircuitBreakerEventListener> listeners;

    public DefaultCircuitBreakerEventPublisher(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void addListener(CircuitBreakerEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "Listener must not be null"));
    }

    @Override
    public boolean removeListener(CircuitBreakerEventListener listener) {
        return listeners.remove(listener);
    }

    public void publishStateTransition(CircuitBreakerState from, CircuitBreakerState to) {
        if (from == to || listeners.isEmpty()) {
            return;
        }
        publish(new CircuitBreakerEvent.StateTransition(clock.instant(), from, to));
    }

    public void publishSuccess(Duration duration, boolean slowCall) {
        if (listeners.isEmpty()) {
            return;
        }

        publish(new CircuitBreakerEvent.CallSucceeded(clock.instant(), duration));
        if (slowCall) {
            publish(new CircuitBreakerEvent.SlowCall(
                    clock.instant(),
                    duration,
                    CircuitBreakerEvent.CallOutcome.SUCCESS
            ));
        }
    }

    public void publishFailure(Duration duration, Throwable exception, boolean slowCall) {
        if (listeners.isEmpty()) {
            return;
        }

        publish(new CircuitBreakerEvent.CallFailed(clock.instant(), duration, exception));
        if (slowCall) {
            publish(new CircuitBreakerEvent.SlowCall(
                    clock.instant(),
                    duration,
                    CircuitBreakerEvent.CallOutcome.FAILURE
            ));
        }
    }

    public void publishIgnored(Duration duration, Throwable exception) {
        if (listeners.isEmpty()) {
            return;
        }
        publish(new CircuitBreakerEvent.CallIgnored(clock.instant(), duration, exception));
    }

    public void publishRejected(CircuitBreakerState state, String reason) {
        if (listeners.isEmpty()) {
            return;
        }
        publish(new CircuitBreakerEvent.CallRejected(clock.instant(), state, reason));
    }

    private void publish(CircuitBreakerEvent event) {
        for (CircuitBreakerEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // Observability callbacks must not affect protected calls.
            }
        }
    }
}
