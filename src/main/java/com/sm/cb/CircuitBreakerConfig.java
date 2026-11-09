package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;

import java.time.Duration;

public final class CircuitBreakerConfig {
    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 0.5f;
    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;
    public static final int DEFAULT_MINIMUM_CALLS = 100;
    public static final SlidingWindowType DEFAULT_SLIDING_WINDOW_TYPE = SlidingWindowType.COUNT_BASED;
    public static final Duration DEFAULT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);
    public static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN = 10;

    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final SlidingWindowType slidingWindowType;
    private final Duration durationInOpenState;
    private final int permittedCallsInHalfOpen;
    private final int minimumCalls;

    private CircuitBreakerConfig(Builder builder) {
        if (Float.isNaN(builder.failureRateThreshold)
                || builder.failureRateThreshold < 0.0f
                || builder.failureRateThreshold > 1.0f) {
            throw new IllegalArgumentException("Failure rate threshold must be between 0 and 1");
        }
        if (builder.slidingWindowSize <= 0) {
            throw new IllegalArgumentException("Sliding window size must be greater than 0");
        }
        if (builder.slidingWindowType == null) {
            throw new IllegalArgumentException("Sliding window type must not be null");
        }
        if (builder.durationInOpenState == null
                || builder.durationInOpenState.isNegative()
                || builder.durationInOpenState.isZero()) {
            throw new IllegalArgumentException("Wait duration must be positive");
        }
        if (builder.permittedCallsInHalfOpen <= 0) {
            throw new IllegalArgumentException("Permitted calls in half-open must be greater than 0");
        }
        if (builder.minimumCalls <= 0) {
            throw new IllegalArgumentException("Minimum number of calls must be greater than 0");
        }
        if (builder.slidingWindowType == SlidingWindowType.COUNT_BASED
                && builder.minimumCalls > builder.slidingWindowSize) {
            throw new IllegalArgumentException("Minimum number of calls must not exceed count-based window size");
        }

        this.failureRateThreshold = builder.failureRateThreshold;
        this.slidingWindowSize = builder.slidingWindowSize;
        this.minimumCalls = builder.minimumCalls;
        this.slidingWindowType = builder.slidingWindowType;
        this.durationInOpenState = builder.durationInOpenState;
        this.permittedCallsInHalfOpen = builder.permittedCallsInHalfOpen;
    }

    public static Builder builder() {
        return new Builder();
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

    public static final class Builder {
        private float failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;
        private int slidingWindowSize = DEFAULT_SLIDING_WINDOW_SIZE;
        private int minimumCalls = DEFAULT_MINIMUM_CALLS;
        private SlidingWindowType slidingWindowType = DEFAULT_SLIDING_WINDOW_TYPE;
        private Duration durationInOpenState = DEFAULT_DURATION_IN_OPEN_STATE;
        private int permittedCallsInHalfOpen = DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN;

        private Builder() {
        }

        public Builder failureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }

        public Builder minimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
            return this;
        }

        public Builder slidingWindowType(SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
            return this;
        }

        public Builder durationInOpenState(Duration durationInOpenState) {
            this.durationInOpenState = durationInOpenState;
            return this;
        }

        public Builder permittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
