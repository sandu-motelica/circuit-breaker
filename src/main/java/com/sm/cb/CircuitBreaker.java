package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.observability.CircuitBreakerStatistics;
import com.sm.cb.observability.CircuitBreakerEventPublisher;
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
    private final HalfOpenCallTracker halfOpenCallTracker;
    private final CircuitBreakerStatisticsCollector statisticsCollector;
    private final DefaultCircuitBreakerEventPublisher eventPublisher;

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
        this.halfOpenCallTracker = new HalfOpenCallTracker();
        this.statisticsCollector = new CircuitBreakerStatisticsCollector();
        this.eventPublisher = new DefaultCircuitBreakerEventPublisher(clock);
    }

    public <T> T execute(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null");
        }

        long callStateVersion = 0;
        CircuitBreakerState stateBeforePermission;
        CircuitBreakerState stateAfterPermission;
        CallNotPermittedException rejection = null;
        synchronized (stateLock) {
            stateBeforePermission = state;
            try {
                checkCallPermitted();
                callStateVersion = stateVersion;
            } catch (CallNotPermittedException e) {
                statisticsCollector.recordRejectedCall();
                rejection = e;
            }
            stateAfterPermission = state;
        }

        eventPublisher.publishStateTransition(stateBeforePermission, stateAfterPermission);
        if (rejection != null) {
            eventPublisher.publishRejected(stateAfterPermission, rejection.getMessage());
            throw rejection;
        }

        Instant callStartedAt = clock.instant();
        T response;
        try {
            response = supplier.get();
        } catch (RuntimeException e) {
            processException(callStateVersion, callStartedAt, e);
            throw e;
        }

        processSuccess(callStateVersion, callStartedAt);
        return response;
    }

    public CircuitBreakerEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public CircuitBreakerStatistics getStatistics() {
        synchronized (stateLock) {
            return statisticsCollector.snapshot();
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

    private void processSuccess(long callStateVersion, Instant callStartedAt) {
        Duration callDuration = getCallDuration(callStartedAt);
        boolean slowCall = isSlowCall(callDuration);
        CircuitBreakerState stateBeforeCompletion;
        CircuitBreakerState stateAfterCompletion;
        synchronized (stateLock) {
            statisticsCollector.recordCall(callDuration, false);
            stateBeforeCompletion = state;
            onSuccess(callStateVersion, slowCall);
            stateAfterCompletion = state;
        }

        eventPublisher.publishSuccess(callDuration, slowCall);
        eventPublisher.publishStateTransition(stateBeforeCompletion, stateAfterCompletion);
    }

    private void processException(
            long callStateVersion,
            Instant callStartedAt,
            RuntimeException exception) {
        Duration callDuration = getCallDuration(callStartedAt);
        boolean slowCall = isSlowCall(callDuration);
        boolean shouldRecordException = config.shouldRecordException(exception);
        CircuitBreakerState stateBeforeCompletion;
        CircuitBreakerState stateAfterCompletion;
        synchronized (stateLock) {
            statisticsCollector.recordCall(callDuration, shouldRecordException);
            stateBeforeCompletion = state;
            if (shouldRecordException) {
                onFailure(callStateVersion, slowCall);
            } else {
                onIgnoredException(callStateVersion);
            }
            stateAfterCompletion = state;
        }

        if (shouldRecordException) {
            eventPublisher.publishFailure(callDuration, exception, slowCall);
        } else {
            eventPublisher.publishIgnored(callDuration, exception);
        }
        eventPublisher.publishStateTransition(stateBeforeCompletion, stateAfterCompletion);
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
            HalfOpenCallTracker.AdmissionResult admissionResult = halfOpenCallTracker.tryAcquire(
                    config.getPermittedCallsInHalfOpen(),
                    config.getMaxConcurrentCallsInHalfOpen()
            );
            if (admissionResult == HalfOpenCallTracker.AdmissionResult.TOTAL_LIMIT_REACHED) {
                throw new CallNotPermittedException("Too many calls in HALF_OPEN");
            }
            if (admissionResult == HalfOpenCallTracker.AdmissionResult.CONCURRENT_LIMIT_REACHED) {
                throw new CallNotPermittedException("Too many concurrent calls in HALF_OPEN");
            }
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
            completeHalfOpenCall(false, slowCall);
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
            completeHalfOpenCall(true, slowCall);
        }
    }

    private void completeHalfOpenCall(boolean failure, boolean slowCall) {
        halfOpenCallTracker.recordCompletion(failure, slowCall);
        if (!halfOpenCallTracker.hasCompleted(config.getPermittedCallsInHalfOpen())) {
            return;
        }

        boolean failureRateExceeded = halfOpenCallTracker.failureCount() > 0
                && halfOpenCallTracker.failureRate() >= config.getHalfOpenFailureRateThreshold();
        boolean slowCallRateExceeded = halfOpenCallTracker.slowCallCount() > 0
                && halfOpenCallTracker.slowCallRate() >= config.getSlowCallRateThreshold();

        if (failureRateExceeded || slowCallRateExceeded) {
            transitionToOpen();
        } else {
            transitionToClosed();
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

    private Duration getCallDuration(Instant callStartedAt) {
        Duration callDuration = Duration.between(callStartedAt, clock.instant());
        return callDuration.isNegative() ? Duration.ZERO : callDuration;
    }

    private boolean isSlowCall(Duration callDuration) {
        return callDuration.compareTo(config.getSlowCallDurationThreshold()) >= 0;
    }

    private void onIgnoredException(long callStateVersion) {
        if (callStateVersion != stateVersion) {
            return;
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenCallTracker.recordIgnoredCall();
        }
    }

    private void transitionToOpen() {
        this.state = CircuitBreakerState.OPEN;
        this.stateVersion++;
        this.openStateAt = clock.instant();
        halfOpenCallTracker.reset();
    }

    private void transitionToHalfOpen() {
        halfOpenCallTracker.reset();
        this.state = CircuitBreakerState.HALF_OPEN;
        this.stateVersion++;
    }

    private void transitionToClosed() {
        this.state = CircuitBreakerState.CLOSED;
        this.stateVersion++;
        halfOpenCallTracker.reset();
        this.slidingWindow.reset();
    }

}
