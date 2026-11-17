package io.github.sandumotelica.circuitbreaker.internal.window;

public class CountBasedSlidingWindow implements SlidingWindow {
    private final boolean[] failureOutcomes;
    private final boolean[] slowCallOutcomes;
    private final int size;

    private int currentIndex;
    private int failureCount;
    private int slowCallCount;
    private int totalCalls;
    private volatile boolean healthyFull;

    public CountBasedSlidingWindow(int size) {
        if (size <= 0) throw new IllegalArgumentException("Window size must be greater than 0");

        this.size = size;
        this.currentIndex = 0;
        this.failureCount = 0;
        this.slowCallCount = 0;
        this.totalCalls = 0;
        this.failureOutcomes = new boolean[size];
        this.slowCallOutcomes = new boolean[size];
    }

    @Override
    public synchronized void recordSuccess(boolean slow) {
        record(false, slow);
    }

    @Override
    public synchronized void recordFailure(boolean slow) {
        record(true, slow);
    }

    @Override
    public SlidingWindowMetrics recordAndGetMetricsLocked(boolean failure, boolean slow) {
        assert Thread.holdsLock(this);
        record(failure, slow);
        return new SlidingWindowMetrics(totalCalls, failureCount, slowCallCount);
    }

    @Override
    public boolean tryRecordHealthySuccess() {
        // Replacing a success with a success cannot change a full healthy window.
        // The volatile read is the linearization point relative to a failure,
        // which clears the flag before changing the ring under its monitor.
        return healthyFull;
    }

    @Override
    public synchronized SlidingWindowMetrics getMetrics() {
        return new SlidingWindowMetrics(totalCalls, failureCount, slowCallCount);
    }

    @Override
    public synchronized void reset() {
        healthyFull = false;
        this.failureCount = 0;
        this.slowCallCount = 0;
        this.currentIndex = 0;
        this.totalCalls = 0;
    }

    private void record(boolean failure, boolean slowCall) {
        healthyFull = false;
        if (totalCalls == size) {
            if (failureOutcomes[currentIndex]) {
                failureCount--;
            }
            if (slowCallOutcomes[currentIndex]) {
                slowCallCount--;
            }
        } else {
            totalCalls++;
        }
        failureOutcomes[currentIndex] = failure;
        slowCallOutcomes[currentIndex] = slowCall;

        if (failure) {
            failureCount++;
        }
        if (slowCall) {
            slowCallCount++;
        }
        currentIndex++;
        if (currentIndex == size) {
            currentIndex = 0;
        }
        healthyFull = totalCalls == size && failureCount == 0 && slowCallCount == 0;
    }
}
