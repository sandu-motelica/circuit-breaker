package io.github.sandumotelica.circuitbreaker.observability;

/**
 * Manages listeners for events emitted by one circuit breaker.
 *
 * <p>Events are delivered synchronously by the thread that emits them. A
 * runtime exception thrown by a listener does not affect the protected call or
 * prevent other listeners from receiving the event.</p>
 */
public interface CircuitBreakerEventPublisher {
    /**
     * Registers a listener for subsequent events.
     *
     * @param listener listener to register
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    void addListener(CircuitBreakerEventListener listener);

    /**
     * Removes one matching listener registration.
     *
     * @param listener listener to remove
     * @return {@code true} if a registration was removed
     */
    boolean removeListener(CircuitBreakerEventListener listener);
}
