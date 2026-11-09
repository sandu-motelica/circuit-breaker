package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.window.CountBasedSlidingWindow;
import com.sm.cb.window.SlidingWindow;
import com.sm.cb.window.SlidingWindowMetrics;
import com.sm.cb.window.SlidingWindowType;
import com.sm.cb.window.TimeBasedSlidingWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class CircuitBreaker {
    private final Clock clock;
    private final CircuitBreakerConfig config;
    private final Object stateLock = new Object();
    private Instant openStateAt;

    private CircuitBreakerState state;
    private long stateVersion;
    private final SlidingWindow slidingWindow;
    private int halfOpenSuccesses;
    private int halfOpenCalls;
    private int halfOpenSlowCalls;

    public CircuitBreaker(CircuitBreakerConfig config) {
        this(config, Clock.systemUTC());
    }

    public CircuitBreaker(CircuitBreakerConfig config, Clock clock) {
        if (config == null) {
            throw new IllegalArgumentException("Undefined circuit breaker config");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }
        this.config = config;
        this.state = CircuitBreakerState.CLOSED;
        this.stateVersion = 0;
        if (config.getSlidingWindowType() == SlidingWindowType.COUNT_BASED) {
            this.slidingWindow = new CountBasedSlidingWindow(config.getSlidingWindowSize());
        } else {
            this.slidingWindow = new TimeBasedSlidingWindow(config.getSlidingWindowSize(), clock);
        }
        this.clock = clock;
        this.halfOpenSuccesses = 0;
        this.halfOpenCalls = 0;
        this.halfOpenSlowCalls = 0;
    }

    public <T> T execute(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null");
        }
        long callStateVersion;
        synchronized (stateLock) {
            checkCallPermitted();
            callStateVersion = stateVersion;
        }
        Instant callStartedAt = clock.instant();

        try {
            T response = supplier.get();
            boolean slowCall = isSlowCall(callStartedAt);
            synchronized (stateLock) {
                onSuccess(callStateVersion, slowCall);
            }
            return response;
        } catch (RuntimeException e) {
            boolean slowCall = isSlowCall(callStartedAt);
            boolean shouldRecordException = config.shouldRecordException(e);
            synchronized (stateLock) {
                if (shouldRecordException) {
                    onFailure(callStateVersion, slowCall);
                } else {
                    onIgnoredException(callStateVersion);
                }
            }
            throw e;
        }
    }

    public CircuitBreakerState getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    public float getFailureRate() {
        synchronized (stateLock) {
            return slidingWindow.getFailureRate();
        }
    }

    public float getSlowCallRate() {
        synchronized (stateLock) {
            return slidingWindow.getSlowCallRate();
        }
    }

    private void checkCallPermitted() {
        if (state == CircuitBreakerState.OPEN) {
            if (Duration.between(openStateAt, clock.instant())
                    .compareTo(config.getDurationInOpenState()) >= 0) {
                transitionToHalfOpen();
            } else {
                throw new CallNotPermittedException("Circuit breaker is OPEN");
            }
        }
        if (state == CircuitBreakerState.HALF_OPEN) {
            if (halfOpenCalls >= config.getPermittedCallsInHalfOpen()) {
                throw new CallNotPermittedException("Too many calls in HALF_OPEN");
            }

            halfOpenCalls++;
        }
    }

    private void onSuccess(long callStateVersion, boolean slowCall) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.CLOSED) {
            slidingWindow.recordSuccess(slowCall);
            evaluateClosedState();
            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenSuccesses++;
            if (slowCall) {
                halfOpenSlowCalls++;
            }

            if (halfOpenSuccesses >= config.getPermittedCallsInHalfOpen()) {
                float slowCallRate = (float) halfOpenSlowCalls / halfOpenSuccesses;
                if (halfOpenSlowCalls > 0
                        && slowCallRate >= config.getSlowCallRateThreshold()) {
                    transitionToOpen();
                } else {
                    transitionToClosed();
                }
            }
        }
    }

    private void onFailure(long callStateVersion, boolean slowCall) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.CLOSED) {
            slidingWindow.recordFailure(slowCall);
            evaluateClosedState();
            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            transitionToOpen();
        }
    }

    private void evaluateClosedState() {
        SlidingWindowMetrics metrics = slidingWindow.getMetrics();
        if (metrics.totalCalls() < config.getMinimumCalls()) {
            return;
        }

        boolean failureRateExceeded = metrics.failedCalls() > 0
                && metrics.failureRate() >= config.getFailureRateThreshold();
        boolean slowCallRateExceeded = metrics.slowCalls() > 0
                && metrics.slowCallRate() >= config.getSlowCallRateThreshold();

        if (failureRateExceeded || slowCallRateExceeded) {
            transitionToOpen();
        }
    }

    private boolean isSlowCall(Instant callStartedAt) {
        Duration callDuration = Duration.between(callStartedAt, clock.instant());
        return callDuration.compareTo(config.getSlowCallDurationThreshold()) >= 0;
    }

    private void onIgnoredException(long callStateVersion) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenCalls--;
        }
    }

    private void transitionToOpen() {
        this.state = CircuitBreakerState.OPEN;
        this.stateVersion++;
        this.openStateAt = clock.instant();
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
        this.halfOpenSlowCalls = 0;
    }

    private void transitionToHalfOpen() {
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
        this.halfOpenSlowCalls = 0;
        this.state = CircuitBreakerState.HALF_OPEN;
        this.stateVersion++;
    }

    private void transitionToClosed() {
        this.state = CircuitBreakerState.CLOSED;
        this.stateVersion++;
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
        this.halfOpenSlowCalls = 0;
        this.slidingWindow.reset();
    }

}
