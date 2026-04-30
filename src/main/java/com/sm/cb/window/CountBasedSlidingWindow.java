package com.sm.cb.window;

public class CountBasedSlidingWindow implements SlidingWindow {
    private final boolean[] outcomes;
    private final int size;

    private int currentIndex;
    private int failures;
    private int totalCalls;


    public CountBasedSlidingWindow(int size) {
        if (size <= 0) throw new IllegalArgumentException("Window size must be greater than 0");

        this.size = size;
        this.currentIndex = 0;
        this.failures = 0;
        this.totalCalls = 0;
        this.outcomes = new boolean[size];
    }

    @Override
    public void recordSuccess() {
        record(true);
    }

    @Override
    public void recordFailure() {
        record(false);
    }

    @Override
    public float getFailureRate() {
        if (totalCalls == 0) return 0.0f;
        return (float) failures / totalCalls;
    }

    @Override
    public void reset() {
        this.failures = 0;
        this.currentIndex = 0;
        this.totalCalls = 0;
    }

    @Override
    public int getTotalCalls() {
        return totalCalls;
    }

    private void record(boolean success) {
        if (totalCalls == size) {
            boolean oldOutcome = outcomes[currentIndex];
            if (!oldOutcome) {
                failures--;
            }
        } else {
            totalCalls++;
        }
        outcomes[currentIndex] = success;

        if (!success) {
            failures++;
        }
        currentIndex = (currentIndex + 1) % size;
    }
}
