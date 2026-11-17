package io.github.sandumotelica.circuitbreaker.internal.window;

public record SlidingWindowMetrics(int totalCalls, int failedCalls, int slowCalls) {

    static boolean exceedsThreshold(int totalCalls, int failedCalls, int slowCalls,
            int minimumCalls, float failureRateThreshold, float slowCallRateThreshold) {
        return totalCalls >= minimumCalls
                && ((failedCalls > 0 && (float) failedCalls / totalCalls >= failureRateThreshold)
                || (slowCalls > 0 && (float) slowCalls / totalCalls >= slowCallRateThreshold));
    }

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
