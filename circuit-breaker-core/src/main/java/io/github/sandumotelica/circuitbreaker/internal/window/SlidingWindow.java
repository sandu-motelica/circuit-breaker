package io.github.sandumotelica.circuitbreaker.internal.window;

public interface SlidingWindow {
    default void recordSuccess() {
        recordSuccess(false);
    }

    default void recordFailure() {
        recordFailure(false);
    }

    void recordSuccess(boolean slow);

    void recordFailure(boolean slow);

    /** Caller must hold this window's monitor. */
    SlidingWindowMetrics recordAndGetMetricsLocked(boolean failure, boolean slow);

    /** Records a call and checks the CLOSED-state thresholds under the window monitor. */
    default boolean recordAndCheckThresholdLocked(boolean failure, boolean slow,
            int minimumCalls, float failureRateThreshold, float slowCallRateThreshold) {
        SlidingWindowMetrics metrics = recordAndGetMetricsLocked(failure, slow);
        return SlidingWindowMetrics.exceedsThreshold(metrics.totalCalls(), metrics.failedCalls(),
                metrics.slowCalls(), minimumCalls, failureRateThreshold, slowCallRateThreshold);
    }

    /** Records a healthy success without the window monitor when it is safe to do so. */
    default boolean tryRecordHealthySuccess() {
        return false;
    }

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
