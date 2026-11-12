package io.github.sandumotelica.circuitbreaker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CircuitBreakerConfig {
    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 0.5f;
    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;
    public static final int DEFAULT_MINIMUM_CALLS = 100;
    public static final SlidingWindowType DEFAULT_SLIDING_WINDOW_TYPE = SlidingWindowType.COUNT_BASED;
    public static final Duration DEFAULT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);
    public static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN = 10;
    public static final float DEFAULT_SLOW_CALL_RATE_THRESHOLD = 1.0f;
    public static final Duration DEFAULT_SLOW_CALL_DURATION_THRESHOLD = Duration.ofSeconds(60);

    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final SlidingWindowType slidingWindowType;
    private final Duration durationInOpenState;
    private final int permittedCallsInHalfOpen;
    private final int maxConcurrentCallsInHalfOpen;
    private final boolean maxConcurrentCallsInHalfOpenCustomized;
    private final float halfOpenFailureRateThreshold;
    private final boolean halfOpenFailureRateThresholdCustomized;
    private final int minimumCalls;
    private final float slowCallRateThreshold;
    private final Duration slowCallDurationThreshold;
    private final List<Class<? extends Throwable>> recordedExceptionTypes;
    private final List<Class<? extends Throwable>> ignoredExceptionTypes;

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
        int maxConcurrentCallsInHalfOpen = builder.maxConcurrentCallsInHalfOpen == null
                ? builder.permittedCallsInHalfOpen
                : builder.maxConcurrentCallsInHalfOpen;
        if (maxConcurrentCallsInHalfOpen <= 0) {
            throw new IllegalArgumentException("Max concurrent calls in half-open must be greater than 0");
        }
        if (maxConcurrentCallsInHalfOpen > builder.permittedCallsInHalfOpen) {
            throw new IllegalArgumentException(
                    "Max concurrent calls in half-open must not exceed permitted calls in half-open");
        }
        if (builder.halfOpenFailureRateThreshold != null
                && (Float.isNaN(builder.halfOpenFailureRateThreshold)
                    || builder.halfOpenFailureRateThreshold < 0.0f
                    || builder.halfOpenFailureRateThreshold > 1.0f)) {
            throw new IllegalArgumentException("Half-open failure rate threshold must be between 0 and 1");
        }
        if (builder.minimumCalls <= 0) {
            throw new IllegalArgumentException("Minimum number of calls must be greater than 0");
        }
        if (Float.isNaN(builder.slowCallRateThreshold)
                || builder.slowCallRateThreshold < 0.0f
                || builder.slowCallRateThreshold > 1.0f) {
            throw new IllegalArgumentException("Slow call rate threshold must be between 0 and 1");
        }
        if (builder.slowCallDurationThreshold == null
                || builder.slowCallDurationThreshold.isNegative()
                || builder.slowCallDurationThreshold.isZero()) {
            throw new IllegalArgumentException("Slow call duration threshold must be positive");
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
        this.maxConcurrentCallsInHalfOpen = maxConcurrentCallsInHalfOpen;
        this.maxConcurrentCallsInHalfOpenCustomized = builder.maxConcurrentCallsInHalfOpen != null;
        this.halfOpenFailureRateThreshold = builder.halfOpenFailureRateThreshold == null
                ? builder.failureRateThreshold
                : builder.halfOpenFailureRateThreshold;
        this.halfOpenFailureRateThresholdCustomized = builder.halfOpenFailureRateThreshold != null;
        this.slowCallRateThreshold = builder.slowCallRateThreshold;
        this.slowCallDurationThreshold = builder.slowCallDurationThreshold;
        this.recordedExceptionTypes = builder.recordedExceptionTypes;
        this.ignoredExceptionTypes = builder.ignoredExceptionTypes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
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

    public int getMaxConcurrentCallsInHalfOpen() {
        return maxConcurrentCallsInHalfOpen;
    }

    public float getHalfOpenFailureRateThreshold() {
        return halfOpenFailureRateThreshold;
    }

    public float getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    public Duration getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    public List<Class<? extends Throwable>> getRecordedExceptionTypes() {
        return recordedExceptionTypes;
    }

    public List<Class<? extends Throwable>> getIgnoredExceptionTypes() {
        return ignoredExceptionTypes;
    }

    /**
     * Returns whether the exception contributes to the failure rate.
     * Ignored types take priority over recorded types.
     */
    public boolean shouldRecordException(Throwable exception) {
        if (exception == null) {
            throw new IllegalArgumentException("Exception must not be null");
        }
        if (matchesExceptionType(exception, ignoredExceptionTypes)) {
            return false;
        }
        return matchesExceptionType(exception, recordedExceptionTypes);
    }

    private static boolean matchesExceptionType(
            Throwable exception,
            List<Class<? extends Throwable>> exceptionTypes) {
        for (Class<? extends Throwable> exceptionType : exceptionTypes) {
            if (exceptionType.isInstance(exception)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<? extends Throwable>> copyExceptionTypes(
            Class<? extends Throwable>[] exceptionTypes,
            String propertyName) {
        if (exceptionTypes == null) {
            throw new IllegalArgumentException(propertyName + " must not be null");
        }

        List<Class<? extends Throwable>> copy = new ArrayList<>(exceptionTypes.length);
        for (Class<? extends Throwable> exceptionType : exceptionTypes) {
            if (exceptionType == null) {
                throw new IllegalArgumentException(propertyName + " must not contain null");
            }
            copy.add(exceptionType);
        }
        return List.copyOf(copy);
    }

    public static final class Builder {
        private float failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;
        private int slidingWindowSize = DEFAULT_SLIDING_WINDOW_SIZE;
        private int minimumCalls = DEFAULT_MINIMUM_CALLS;
        private SlidingWindowType slidingWindowType = DEFAULT_SLIDING_WINDOW_TYPE;
        private Duration durationInOpenState = DEFAULT_DURATION_IN_OPEN_STATE;
        private int permittedCallsInHalfOpen = DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN;
        private Integer maxConcurrentCallsInHalfOpen;
        private Float halfOpenFailureRateThreshold;
        private float slowCallRateThreshold = DEFAULT_SLOW_CALL_RATE_THRESHOLD;
        private Duration slowCallDurationThreshold = DEFAULT_SLOW_CALL_DURATION_THRESHOLD;
        private List<Class<? extends Throwable>> recordedExceptionTypes = List.of(Throwable.class);
        private List<Class<? extends Throwable>> ignoredExceptionTypes = List.of();

        private Builder() {
        }

        private Builder(CircuitBreakerConfig config) {
            this.failureRateThreshold = config.failureRateThreshold;
            this.slidingWindowSize = config.slidingWindowSize;
            this.minimumCalls = config.minimumCalls;
            this.slidingWindowType = config.slidingWindowType;
            this.durationInOpenState = config.durationInOpenState;
            this.permittedCallsInHalfOpen = config.permittedCallsInHalfOpen;
            this.maxConcurrentCallsInHalfOpen = config.maxConcurrentCallsInHalfOpenCustomized
                    ? config.maxConcurrentCallsInHalfOpen
                    : null;
            this.halfOpenFailureRateThreshold = config.halfOpenFailureRateThresholdCustomized
                    ? config.halfOpenFailureRateThreshold
                    : null;
            this.slowCallRateThreshold = config.slowCallRateThreshold;
            this.slowCallDurationThreshold = config.slowCallDurationThreshold;
            this.recordedExceptionTypes = config.recordedExceptionTypes;
            this.ignoredExceptionTypes = config.ignoredExceptionTypes;
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

        public Builder maxConcurrentCallsInHalfOpen(int maxConcurrentCallsInHalfOpen) {
            this.maxConcurrentCallsInHalfOpen = maxConcurrentCallsInHalfOpen;
            return this;
        }

        public Builder halfOpenFailureRateThreshold(float halfOpenFailureRateThreshold) {
            this.halfOpenFailureRateThreshold = halfOpenFailureRateThreshold;
            return this;
        }

        public Builder slowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
            return this;
        }

        public Builder slowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
            return this;
        }

        @SafeVarargs
        public final Builder recordExceptions(Class<? extends Throwable>... exceptionTypes) {
            this.recordedExceptionTypes = copyExceptionTypes(exceptionTypes, "Recorded exception types");
            return this;
        }

        @SafeVarargs
        public final Builder ignoreExceptions(Class<? extends Throwable>... exceptionTypes) {
            this.ignoredExceptionTypes = copyExceptionTypes(exceptionTypes, "Ignored exception types");
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
