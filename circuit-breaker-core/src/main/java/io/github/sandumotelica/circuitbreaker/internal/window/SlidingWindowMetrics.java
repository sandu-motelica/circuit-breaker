package io.github.sandumotelica.circuitbreaker.internal.window;

public record SlidingWindowMetrics(int totalCalls, int failedCalls, int slowCalls) {

    public float failureRate() {
        return rateOf(failedCalls);
    }

    public float slowCallRate() {
        return rateOf(slowCalls);
    }

    private float rateOf(int calls) {
        if (totalCalls == 0) {
            return 0.0f;
        }
        return (float) calls / totalCalls;
    }
}
