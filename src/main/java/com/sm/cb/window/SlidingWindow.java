package com.sm.cb.window;

public interface SlidingWindow {
    void recordSuccess();

    void recordFailure();

    float getFailureRate();

    void reset();
}
