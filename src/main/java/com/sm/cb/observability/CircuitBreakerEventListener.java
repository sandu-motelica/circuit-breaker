package com.sm.cb.observability;

@FunctionalInterface
public interface CircuitBreakerEventListener {
    void onEvent(CircuitBreakerEvent event);
}
