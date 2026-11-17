package io.github.sandumotelica.circuitbreaker.scenario;

enum Target {
    CUSTOM("/api/payments/", "/api/circuit-breaker"),
    RESILIENCE4J("/api/payments/resilience4j/", "/api/resilience4j/circuit-breaker");

    private final String paymentPath;
    private final String breakerPath;

    Target(String paymentPath, String breakerPath) {
        this.paymentPath = paymentPath;
        this.breakerPath = breakerPath;
    }

    String paymentPath() {
        return paymentPath;
    }

    String breakerPath() {
        return breakerPath;
    }

    String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
