package com.sm.cb.window;

public interface SlidingWindow {
    default void recordSuccess() {
        recordSuccess(false);
    }

    default void recordFailure() {
        recordFailure(false);
    }

    void recordSuccess(boolean slow);

    void recordFailure(boolean slow);

    SlidingWindowMetrics getMetrics();

    default float getFailureRate() {
        return getMetrics().failureRate();
    }

    default float getSlowCallRate() {
        return getMetrics().slowCallRate();
    }

    void reset();

    default int getTotalCalls() {
        return getMetrics().totalCalls();
    }
}
