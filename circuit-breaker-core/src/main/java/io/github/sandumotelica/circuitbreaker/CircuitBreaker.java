package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEventPublisher;
import io.github.sandumotelica.circuitbreaker.internal.CircuitBreakerStatisticsCollector;
import io.github.sandumotelica.circuitbreaker.internal.DefaultCircuitBreakerEventPublisher;
import io.github.sandumotelica.circuitbreaker.internal.HalfOpenCallTracker;
import io.github.sandumotelica.circuitbreaker.internal.window.CountBasedSlidingWindow;
import io.github.sandumotelica.circuitbreaker.internal.window.SlidingWindow;
import io.github.sandumotelica.circuitbreaker.internal.window.SlidingWindowMetrics;
import io.github.sandumotelica.circuitbreaker.internal.window.TimeBasedSlidingWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class CircuitBreaker {
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

        CallPermission callPermission = acquireCallPermission();
        if (callPermission.ignoreResults()) {
            return supplier.get();
        }

        Instant callStartedAt = clock.instant();
        T response;
        try {
            response = supplier.get();
        } catch (RuntimeException e) {
            processException(callPermission.stateVersion(), callStartedAt, e);
            throw e;
        }

        processSuccess(callPermission.stateVersion(), callStartedAt);
        return response;
    }

    public <T> CompletableFuture<T> executeAsync(
            Supplier<? extends CompletableFuture<T>> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null");
        }

        CallPermission callPermission;
        try {
            callPermission = acquireCallPermission();
        } catch (CallNotPermittedException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        if (callPermission.ignoreResults()) {
            try {
                CompletableFuture<T> future = supplier.get();
                return Objects.requireNonNullElseGet(future, () -> CompletableFuture.failedFuture(
                        new IllegalStateException("Async supplier must not return null")
                ));
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        Instant callStartedAt = clock.instant();
        CompletableFuture<T> sourceFuture;
        try {
            sourceFuture = supplier.get();
            if (sourceFuture == null) {
                throw new IllegalStateException("Async supplier must not return null");
            }
        } catch (RuntimeException exception) {
            processException(callPermission.stateVersion(), callStartedAt, exception);
            return CompletableFuture.failedFuture(exception);
        }

        CompletableFuture<T> decoratedFuture = new CompletableFuture<>();
        sourceFuture.whenComplete((result, exception) -> {
            try {
                if (exception == null) {
                    processSuccess(callPermission.stateVersion(), callStartedAt);
                } else {
                    processException(
                            callPermission.stateVersion(),
                            callStartedAt,
                            unwrapCompletionException(exception)
                    );
                }
            } finally {
                if (exception == null) {
                    decoratedFuture.complete(result);
                } else {
                    decoratedFuture.completeExceptionally(exception);
                }
            }
        });
        return decoratedFuture;
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

    public void transitionToClosedState() {
        transitionManually(CircuitBreakerState.CLOSED);
    }

    public void transitionToOpenState() {
        transitionManually(CircuitBreakerState.OPEN);
    }

    public void transitionToHalfOpenState() {
        transitionManually(CircuitBreakerState.HALF_OPEN);
    }

    public void transitionToForcedOpenState() {
        transitionManually(CircuitBreakerState.FORCED_OPEN);
    }

    public void transitionToDisabledState() {
        transitionManually(CircuitBreakerState.DISABLED);
    }

    public void transitionToMetricsOnlyState() {
        transitionManually(CircuitBreakerState.METRICS_ONLY);
    }

    /**
     * Restores the initial CLOSED state and clears all collected metrics.
     */
    public void reset() {
        CircuitBreakerState previousState;
        synchronized (stateLock) {
            previousState = state;
            changeState(CircuitBreakerState.CLOSED, true);
            statisticsCollector.reset();
        }
        eventPublisher.publishStateTransition(previousState, CircuitBreakerState.CLOSED);
    }

    private CallPermission acquireCallPermission() {
        long callStateVersion = 0;
        CircuitBreakerState stateBeforePermission;
        CircuitBreakerState stateAfterPermission;
        CallNotPermittedException rejection = null;
        synchronized (stateLock) {
            stateBeforePermission = state;
            try {
                checkCallPermitted();
                callStateVersion = stateVersion;
            } catch (CallNotPermittedException exception) {
                statisticsCollector.recordRejectedCall();
                rejection = exception;
            }
            stateAfterPermission = state;
        }

        eventPublisher.publishStateTransition(stateBeforePermission, stateAfterPermission);
        if (rejection != null) {
            eventPublisher.publishRejected(stateAfterPermission, rejection.getMessage());
            throw rejection;
        }
        return new CallPermission(callStateVersion, stateAfterPermission);
    }

    private void transitionManually(CircuitBreakerState targetState) {
        CircuitBreakerState previousState;
        synchronized (stateLock) {
            previousState = state;
            if (previousState == targetState) {
                return;
            }
            changeState(targetState, targetState.resetsSlidingWindowOnManualTransition());
        }
        eventPublisher.publishStateTransition(previousState, targetState);
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
            Throwable exception) {
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

    private Throwable unwrapCompletionException(Throwable exception) {
        Throwable unwrapped = exception;
        while (unwrapped instanceof CompletionException && unwrapped.getCause() != null) {
            unwrapped = unwrapped.getCause();
        }
        return unwrapped;
    }

    private void checkCallPermitted() {
        if (state == CircuitBreakerState.FORCED_OPEN) {
            throw new CallNotPermittedException("Circuit breaker is FORCED_OPEN");
        }
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

        if (state == CircuitBreakerState.METRICS_ONLY) {
            slidingWindow.recordSuccess(slowCall);
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

        if (state == CircuitBreakerState.METRICS_ONLY) {
            slidingWindow.recordFailure(slowCall);
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
        changeState(CircuitBreakerState.OPEN, false);
    }

    private void transitionToHalfOpen() {
        changeState(CircuitBreakerState.HALF_OPEN, false);
    }

    private void transitionToClosed() {
        changeState(CircuitBreakerState.CLOSED, true);
    }

    private void changeState(CircuitBreakerState targetState, boolean resetSlidingWindow) {
        this.state = targetState;
        this.stateVersion++;
        this.openStateAt = targetState == CircuitBreakerState.OPEN ? clock.instant() : null;
        halfOpenCallTracker.reset();
        if (resetSlidingWindow) {
            slidingWindow.reset();
        }
    }

    private record CallPermission(long stateVersion, CircuitBreakerState state) {
        private boolean ignoreResults() {
            return !state.recordsResults();
        }
    }

}
