package io.github.sandumotelica.circuitbreaker.observability;

public interface CircuitBreakerEventPublisher {
    void addListener(CircuitBreakerEventListener listener);

    boolean removeListener(CircuitBreakerEventListener listener);
}
