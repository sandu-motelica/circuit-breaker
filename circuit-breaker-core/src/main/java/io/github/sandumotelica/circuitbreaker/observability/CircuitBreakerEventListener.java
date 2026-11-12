package io.github.sandumotelica.circuitbreaker.observability;

@FunctionalInterface
public interface CircuitBreakerEventListener {
    void onEvent(CircuitBreakerEvent event);
}
