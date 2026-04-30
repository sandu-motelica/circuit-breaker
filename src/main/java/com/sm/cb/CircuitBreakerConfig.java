package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;

import java.time.Duration;

public class CircuitBreakerConfig {
    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final SlidingWindowType slidingWindowType;
    private final Duration durationInOpenState;
    private final int permittedCallsInHalfOpen;
    private final int minimumCalls;

    public CircuitBreakerConfig(float failureRateThreshold, int slidingWindowSize, int minimumCalls, SlidingWindowType slidingWindowType, Duration durationInOpenState, int permittedCallsInHalfOpen) {
        if (failureRateThreshold < 0.0f || failureRateThreshold > 1.0f) {
            throw new IllegalArgumentException("Failure rate threshold must be between 0 and 1");
        }
        if (slidingWindowSize <= 0) {
            throw new IllegalArgumentException("Sliding window size must be greater than 0");
        }
        if (durationInOpenState == null || durationInOpenState.isNegative() || durationInOpenState.isZero()) {
            throw new IllegalArgumentException("Wait duration must be positive");
        }
        if (permittedCallsInHalfOpen <= 0) {
            throw new IllegalArgumentException("Permitted calls in half-open must be greater than 0");
        }
        if (minimumCalls <= 0) {
            throw new IllegalArgumentException("Minimum number of calls must be greater than 0");
        }

        this.failureRateThreshold = failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize;
        this.minimumCalls = minimumCalls;
        this.slidingWindowType = slidingWindowType;
        this.durationInOpenState = durationInOpenState;
        this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public int getMinimumCalls() {
        return minimumCalls;
    }

    public SlidingWindowType getSlidingWindowType() {
        return slidingWindowType;
    }

    public Duration getDurationInOpenState() {
        return durationInOpenState;
    }

    public int getPermittedCallsInHalfOpen() {
        return permittedCallsInHalfOpen;
    }
}
