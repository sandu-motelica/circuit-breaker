package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CircuitBreakerState;

record CircuitBreakerStatus(
        CircuitBreakerState state,
        float failureRate,
        float slowCallRate,
        long totalCalls,
        long failedCalls,
        long rejectedCalls,
        long averageDurationMillis) {
}
