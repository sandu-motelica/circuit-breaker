package io.github.sandumotelica.circuitbreaker.demo.client;

record Resilience4jCircuitBreakerStatus(
        String state,
        float failureRate,
        float slowCallRate,
        int bufferedCalls,
        int failedCalls,
        long notPermittedCalls) {
}
