package com.sm.cb.window;

public class CountBasedSlidingWindow implements SlidingWindow {
    private final boolean[] failureOutcomes;
    private final boolean[] slowCallOutcomes;
    private final int size;

    private int currentIndex;
    private int failureCount;
    private int slowCallCount;
    private int totalCalls;


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
    public synchronized SlidingWindowMetrics getMetrics() {
        return new SlidingWindowMetrics(totalCalls, failureCount, slowCallCount);
    }

    @Override
    public synchronized void reset() {
        this.failureCount = 0;
        this.slowCallCount = 0;
        this.currentIndex = 0;
        this.totalCalls = 0;
    }

    private void record(boolean failure, boolean slowCall) {
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
        currentIndex = (currentIndex + 1) % size;
    }
}
