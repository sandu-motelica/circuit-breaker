package io.github.sandumotelica.circuitbreaker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable configuration for a {@link CircuitBreaker}.
 *
 * <p>Rate thresholds are expressed as values from {@code 0.0} to {@code 1.0}.
 * Create instances with {@link #builder()} and derive modified copies with
 * {@link #toBuilder()}.</p>
 */
public final class CircuitBreakerConfig {
    /** Default failure-rate threshold: {@value}. */
    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 0.5f;
    /** Default sliding-window size: {@value}. */
    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;
    /** Default minimum number of calls required before evaluation: {@value}. */
    public static final int DEFAULT_MINIMUM_CALLS = 100;
    /** Default sliding-window type. */
    public static final SlidingWindowType DEFAULT_SLIDING_WINDOW_TYPE = SlidingWindowType.COUNT_BASED;
    /** Default duration spent in the open state. */
    public static final Duration DEFAULT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);
    /** Default number of probe calls permitted in the half-open state: {@value}. */
    public static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN = 10;
    /** Default slow-call-rate threshold: {@value}. */
    public static final float DEFAULT_SLOW_CALL_RATE_THRESHOLD = 1.0f;
    /** Default duration at which a call is classified as slow. */
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

    /**
     * Creates a builder initialized with the default configuration.
     *
     * @return a new configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized from this configuration.
     *
     * @return a builder containing this configuration's values
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns the failure-rate threshold that opens the circuit.
     *
     * @return failure-rate threshold from {@code 0.0} to {@code 1.0}
     */
    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /**
     * Returns the sliding-window size, measured in calls for a count-based window
     * and seconds for a time-based window.
     *
     * @return positive window size
     */
    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /**
     * Returns the minimum number of calls required before rates are evaluated.
     *
     * @return positive minimum call count
     */
    public int getMinimumCalls() {
        return minimumCalls;
    }

    /**
     * Returns the sliding-window strategy.
     *
     * @return configured window type
     */
    public SlidingWindowType getSlidingWindowType() {
        return slidingWindowType;
    }

    /**
     * Returns how long the circuit remains open before admitting half-open probes.
     *
     * @return positive open-state duration
     */
    public Duration getDurationInOpenState() {
        return durationInOpenState;
    }

    /**
     * Returns the number of completed probe calls used to evaluate the half-open state.
     *
     * @return positive probe-call count
     */
    public int getPermittedCallsInHalfOpen() {
        return permittedCallsInHalfOpen;
    }

    /**
     * Returns the maximum number of half-open probe calls that may run concurrently.
     *
     * @return positive concurrent-call limit
     */
    public int getMaxConcurrentCallsInHalfOpen() {
        return maxConcurrentCallsInHalfOpen;
    }

    /**
     * Returns the failure-rate threshold used to evaluate half-open probes.
     *
     * @return half-open failure-rate threshold from {@code 0.0} to {@code 1.0}
     */
    public float getHalfOpenFailureRateThreshold() {
        return halfOpenFailureRateThreshold;
    }

    /**
     * Returns the slow-call-rate threshold that opens the circuit.
     *
     * @return slow-call-rate threshold from {@code 0.0} to {@code 1.0}
     */
    public float getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    /**
     * Returns the duration at or above which a call is classified as slow.
     *
     * @return positive slow-call duration threshold
     */
    public Duration getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    /**
     * Returns the exception types that may contribute to the failure rate.
     *
     * @return immutable list of recorded exception types
     */
    public List<Class<? extends Throwable>> getRecordedExceptionTypes() {
        return recordedExceptionTypes;
    }

    /**
     * Returns the exception types excluded from the failure rate.
     *
     * @return immutable list of ignored exception types
     */
    public List<Class<? extends Throwable>> getIgnoredExceptionTypes() {
        return ignoredExceptionTypes;
    }

    /**
     * Returns whether the exception contributes to the failure rate.
     * Ignored types take priority over recorded types, and subtype matches are included.
     *
     * @param exception exception to classify
     * @return {@code true} if the exception must be recorded as a failure
     * @throws IllegalArgumentException if {@code exception} is {@code null}
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

    /**
     * Builds immutable circuit-breaker configurations.
     *
     * <p>Unless explicitly overridden, half-open concurrency equals the permitted
     * half-open call count and the half-open failure threshold inherits the main
     * failure threshold.</p>
     */
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

        /**
         * Sets the failure-rate threshold that opens the circuit.
         *
         * @param failureRateThreshold value from {@code 0.0} to {@code 1.0}
         * @return this builder
         */
        public Builder failureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        /**
         * Sets the window size in calls for a count-based window or seconds for a
         * time-based window.
         *
         * @param slidingWindowSize positive window size
         * @return this builder
         */
        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }

        /**
         * Sets the minimum number of calls required before threshold evaluation.
         *
         * @param minimumCalls positive minimum call count
         * @return this builder
         */
        public Builder minimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
            return this;
        }

        /**
         * Sets the sliding-window strategy.
         *
         * @param slidingWindowType window type
         * @return this builder
         */
        public Builder slidingWindowType(SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
            return this;
        }

        /**
         * Sets how long the circuit remains open before half-open probes are admitted.
         *
         * @param durationInOpenState positive duration
         * @return this builder
         */
        public Builder durationInOpenState(Duration durationInOpenState) {
            this.durationInOpenState = durationInOpenState;
            return this;
        }

        /**
         * Sets the number of completed probes used to evaluate the half-open state.
         *
         * @param permittedCallsInHalfOpen positive probe-call count
         * @return this builder
         */
        public Builder permittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
            return this;
        }

        /**
         * Sets the maximum number of half-open probes that may run concurrently.
         *
         * @param maxConcurrentCallsInHalfOpen positive limit that does not exceed
         *        the permitted half-open call count
         * @return this builder
         */
        public Builder maxConcurrentCallsInHalfOpen(int maxConcurrentCallsInHalfOpen) {
            this.maxConcurrentCallsInHalfOpen = maxConcurrentCallsInHalfOpen;
            return this;
        }

        /**
         * Sets the failure-rate threshold used to evaluate half-open probes.
         *
         * @param halfOpenFailureRateThreshold value from {@code 0.0} to {@code 1.0}
         * @return this builder
         */
        public Builder halfOpenFailureRateThreshold(float halfOpenFailureRateThreshold) {
            this.halfOpenFailureRateThreshold = halfOpenFailureRateThreshold;
            return this;
        }

        /**
         * Sets the slow-call-rate threshold that opens the circuit.
         *
         * @param slowCallRateThreshold value from {@code 0.0} to {@code 1.0}
         * @return this builder
         */
        public Builder slowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
            return this;
        }

        /**
         * Sets the duration at or above which a call is classified as slow.
         *
         * @param slowCallDurationThreshold positive duration
         * @return this builder
         */
        public Builder slowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
            return this;
        }

        /**
         * Replaces the exception types that contribute to the failure rate.
         * Subtypes of the configured classes are included.
         *
         * @param exceptionTypes exception types to record
         * @return this builder
         * @throws IllegalArgumentException if the array or any element is {@code null}
         */
        @SafeVarargs
        public final Builder recordExceptions(Class<? extends Throwable>... exceptionTypes) {
            this.recordedExceptionTypes = copyExceptionTypes(exceptionTypes, "Recorded exception types");
            return this;
        }

        /**
         * Replaces the exception types ignored by failure-rate evaluation.
         * Ignored types take priority over recorded types.
         *
         * @param exceptionTypes exception types to ignore
         * @return this builder
         * @throws IllegalArgumentException if the array or any element is {@code null}
         */
        @SafeVarargs
        public final Builder ignoreExceptions(Class<? extends Throwable>... exceptionTypes) {
            this.ignoredExceptionTypes = copyExceptionTypes(exceptionTypes, "Ignored exception types");
            return this;
        }

        /**
         * Validates the configured values and creates an immutable configuration.
         *
         * @return a new circuit-breaker configuration
         * @throws IllegalArgumentException if any configured value is invalid
         */
        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
