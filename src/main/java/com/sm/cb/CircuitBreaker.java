package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.window.CountBasedSlidingWindow;
import com.sm.cb.window.SlidingWindow;
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

        try {
            T response = supplier.get();
            synchronized (stateLock) {
                onSuccess(callStateVersion);
            }
            return response;
        } catch (RuntimeException e) {
            boolean shouldRecordException = config.shouldRecordException(e);
            synchronized (stateLock) {
                if (shouldRecordException) {
                    onFailure(callStateVersion);
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

    private void onSuccess(long callStateVersion) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.CLOSED) {
            slidingWindow.recordSuccess();
            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenSuccesses++;

            if (halfOpenSuccesses >= config.getPermittedCallsInHalfOpen()) {
                transitionToClosed();
            }
        }
    }

    private void onFailure(long callStateVersion) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.CLOSED) {
            slidingWindow.recordFailure();

            if (slidingWindow.getTotalCalls() >= config.getMinimumCalls() && slidingWindow.getFailureRate() >= config.getFailureRateThreshold()) {
                transitionToOpen();
            }

            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            transitionToOpen();
        }
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
    }

    private void transitionToHalfOpen() {
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
        this.state = CircuitBreakerState.HALF_OPEN;
        this.stateVersion++;
    }

    private void transitionToClosed() {
        this.state = CircuitBreakerState.CLOSED;
        this.stateVersion++;
        this.slidingWindow.reset();
    }

}
