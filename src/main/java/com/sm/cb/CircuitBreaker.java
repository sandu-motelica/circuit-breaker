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
    private Instant openStateAt;

    private CircuitBreakerState state;
    private final SlidingWindow slidingWindow;
    private int halfOpenSuccesses;
    private int halfOpenCalls;

    public CircuitBreaker(CircuitBreakerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Undefined circuit breaker config");
        }
        this.config = config;
        this.state = CircuitBreakerState.CLOSED;
        if (config.getSlidingWindowType() == SlidingWindowType.COUNT_BASED) {
            this.slidingWindow = new CountBasedSlidingWindow(config.getSlidingWindowSize());
        } else {
            this.slidingWindow = new TimeBasedSlidingWindow(config.getSlidingWindowSize());
        }
        this.clock = Clock.systemUTC();
        this.halfOpenSuccesses = 0;
        this.halfOpenCalls = 0;
    }

    public <T> T execute(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null");
        }
        checkCallPermitted();

        try {
            T response = supplier.get();
            onSuccess();
            return response;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public float getFailureRate() {
        return slidingWindow.getFailureRate();
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


    private void onSuccess() {
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

    private void onFailure() {
        if (state == CircuitBreakerState.CLOSED) {
            slidingWindow.recordFailure();

            if (slidingWindow.getFailureRate() >= config.getFailureRateThreshold()) {
                transitionToOpen();
            }

            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            transitionToOpen();
        }
    }

    private void transitionToOpen() {
        this.state = CircuitBreakerState.OPEN;
        this.openStateAt = clock.instant();
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
    }

    private void transitionToHalfOpen() {
        this.halfOpenCalls = 0;
        this.halfOpenSuccesses = 0;
        this.state = CircuitBreakerState.HALF_OPEN;
    }

    private void transitionToClosed() {
        this.state = CircuitBreakerState.CLOSED;
        this.slidingWindow.reset();
    }

}
