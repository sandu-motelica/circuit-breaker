package com.sm.cb.observability;

public interface CircuitBreakerEventPublisher {
    void addListener(CircuitBreakerEventListener listener);

    boolean removeListener(CircuitBreakerEventListener listener);
}
