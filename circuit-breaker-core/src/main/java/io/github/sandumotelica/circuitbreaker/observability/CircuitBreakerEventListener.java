package io.github.sandumotelica.circuitbreaker.observability;

/**
 * Receives events emitted by a circuit breaker.
 */
@FunctionalInterface
public interface CircuitBreakerEventListener {
    /**
     * Handles one circuit-breaker event.
     *
     * @param event emitted event
     */
    void onEvent(CircuitBreakerEvent event);
}
