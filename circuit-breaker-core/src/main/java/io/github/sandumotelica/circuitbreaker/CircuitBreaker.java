package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEventPublisher;
import io.github.sandumotelica.circuitbreaker.internal.CircuitBreakerStatisticsCollector;
import io.github.sandumotelica.circuitbreaker.internal.DefaultCircuitBreakerEventPublisher;
import io.github.sandumotelica.circuitbreaker.internal.HalfOpenCallTracker;
import io.github.sandumotelica.circuitbreaker.internal.window.CountBasedSlidingWindow;
import io.github.sandumotelica.circuitbreaker.internal.window.SlidingWindow;
import io.github.sandumotelica.circuitbreaker.internal.window.TimeBasedSlidingWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Protects synchronous and asynchronous operations from repeatedly calling an
 * unhealthy dependency.
 *
 * <p>The breaker records failures and slow calls in a sliding window. Once a
 * configured threshold is reached, it transitions from {@link CircuitBreakerState#CLOSED}
 * to {@link CircuitBreakerState#OPEN} and rejects new calls. After the configured
 * open-state duration, a limited number of probe calls are admitted in
 * {@link CircuitBreakerState#HALF_OPEN}.</p>
 *
 * <p>Instances are thread-safe.</p>
 */
public final class CircuitBreaker {
    private final Clock clock;
    private final CircuitBreakerConfig config;
    private Instant openStateAt;

    private CircuitBreakerState state;
    private long stateVersion;
    private volatile CallPermission admissionState;
    private final SlidingWindow slidingWindow;
    private HalfOpenCallTracker halfOpenCallTracker;
    private volatile CircuitBreakerStatisticsCollector statisticsCollector;
    private final DefaultCircuitBreakerEventPublisher eventPublisher;

    /**
     * Creates a circuit breaker using the system UTC clock.
     *
     * @param config configuration used for all state transitions and metrics
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    public CircuitBreaker(CircuitBreakerConfig config) {
        this(config, Clock.systemUTC());
    }

    /**
     * Creates a circuit breaker using the supplied clock.
     *
     * <p>A custom clock is useful when call durations and state transitions must
     * use an application-specific time source.</p>
     *
     * @param config configuration used for all state transitions and metrics
     * @param clock  time source used to measure calls and open-state duration
     * @throws IllegalArgumentException if {@code config} or {@code clock} is {@code null}
     */
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
        this.halfOpenCallTracker = new HalfOpenCallTracker();
        this.admissionState = new CallPermission(0, CircuitBreakerState.CLOSED, halfOpenCallTracker);
        if (config.getSlidingWindowType() == SlidingWindowType.COUNT_BASED) {
            this.slidingWindow = new CountBasedSlidingWindow(config.getSlidingWindowSize());
        } else {
            this.slidingWindow = new TimeBasedSlidingWindow(config.getSlidingWindowSize(), clock);
        }
        this.clock = clock;
        this.statisticsCollector = new CircuitBreakerStatisticsCollector();
        this.eventPublisher = new DefaultCircuitBreakerEventPublisher(clock);
    }

    /**
     * Executes an operation under circuit-breaker protection.
     *
     * @param supplier operation to execute
     * @param <T>      result type
     * @return the operation result
     * @throws CallNotPermittedException if the current state rejects the call
     * @throws IllegalArgumentException  if {@code supplier} is {@code null}
     * @throws RuntimeException          if the operation throws one
     */
    public <T> T execute(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier must not be null");
        }

        CallPermission callPermission = acquireCallPermission();
        if (callPermission.ignoreResults()) {
            return supplier.get();
        }

        Instant callStartedAt = clock.instant();
        long startSecond = callStartedAt.getEpochSecond();
        int startNano = callStartedAt.getNano();
        T response;
        try {
            response = supplier.get();
        } catch (RuntimeException e) {
            processException(callPermission.stateVersion(), startSecond, startNano, e);
            throw e;
        }

        processSuccess(callPermission.stateVersion(), startSecond, startNano);
        return response;
    }

    /**
     * Starts an asynchronous operation under circuit-breaker protection and
     * records its outcome when the returned future completes.
     *
     * <p>Rejections and exceptions thrown while obtaining the source future are
     * represented by an exceptionally completed future.</p>
     *
     * @param supplier operation that starts and returns the asynchronous computation
     * @param <T>      result type
     * @return a future completed with the operation result or exception
     * @throws IllegalArgumentException if {@code supplier} is {@code null}
     */
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
        long startSecond = callStartedAt.getEpochSecond();
        int startNano = callStartedAt.getNano();
        CompletableFuture<T> sourceFuture;
        try {
            sourceFuture = supplier.get();
            if (sourceFuture == null) {
                throw new IllegalStateException("Async supplier must not return null");
            }
        } catch (RuntimeException exception) {
            processException(callPermission.stateVersion(), startSecond, startNano, exception);
            return CompletableFuture.failedFuture(exception);
        }

        CompletableFuture<T> decoratedFuture = new CompletableFuture<>();
        sourceFuture.whenComplete((result, exception) -> {
            try {
                if (exception == null) {
                    processSuccess(callPermission.stateVersion(), startSecond, startNano);
                } else {
                    processException(
                            callPermission.stateVersion(),
                            startSecond,
                            startNano,
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

    /**
     * Returns the publisher used to subscribe to circuit-breaker events.
     *
     * @return this breaker's event publisher
     */
    public CircuitBreakerEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    /**
     * Returns a snapshot of cumulative call statistics collected since creation
     * or the latest {@link #reset()}.
     * During concurrent updates, individual counters may reflect slightly
     * different instants. Once updates finish, the snapshot is exact.
     *
     * @return an immutable statistics snapshot
     */
    public CircuitBreakerStatistics getStatistics() {
        return statisticsCollector.snapshot();
    }

    /**
     * Returns the current circuit-breaker state.
     *
     * @return current state
     */
    public CircuitBreakerState getState() {
        return admissionState.state();
    }

    /**
     * Returns the failure rate in the main sliding window.
     *
     * @return a value from {@code 0.0} to {@code 1.0}
     */
    public float getFailureRate() {
        return slidingWindow.getFailureRate();
    }

    /**
     * Returns the slow-call rate in the main sliding window.
     *
     * @return a value from {@code 0.0} to {@code 1.0}
     */
    public float getSlowCallRate() {
        return slidingWindow.getSlowCallRate();
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#CLOSED} and clears the
     * sliding-window metrics.
     */
    public void transitionToClosedState() {
        transitionManually(CircuitBreakerState.CLOSED);
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#OPEN}.
     */
    public void transitionToOpenState() {
        transitionManually(CircuitBreakerState.OPEN);
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#HALF_OPEN}.
     */
    public void transitionToHalfOpenState() {
        transitionManually(CircuitBreakerState.HALF_OPEN);
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#FORCED_OPEN}, causing
     * every call to be rejected until another manual transition occurs.
     */
    public void transitionToForcedOpenState() {
        transitionManually(CircuitBreakerState.FORCED_OPEN);
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#DISABLED}, allowing all
     * calls through without recording their results.
     */
    public void transitionToDisabledState() {
        transitionManually(CircuitBreakerState.DISABLED);
    }

    /**
     * Manually transitions to {@link CircuitBreakerState#METRICS_ONLY}, allowing
     * all calls while continuing to record their results.
     */
    public void transitionToMetricsOnlyState() {
        transitionManually(CircuitBreakerState.METRICS_ONLY);
    }

    /**
     * Restores the initial {@link CircuitBreakerState#CLOSED} state and clears
     * sliding-window metrics, half-open probes, and cumulative statistics.
     */
    public void reset() {
        CircuitBreakerState previousState;
        synchronized (slidingWindow) {
            previousState = state;
            statisticsCollector = new CircuitBreakerStatisticsCollector();
            changeState(CircuitBreakerState.CLOSED, true);
        }
        eventPublisher.publishStateTransition(previousState, CircuitBreakerState.CLOSED);
    }

    private CallPermission acquireCallPermission() {
        while (true) {
            CallPermission current = admissionState;
            if (current.state() == CircuitBreakerState.CLOSED
                    || current.state() == CircuitBreakerState.METRICS_ONLY
                    || current.state() == CircuitBreakerState.DISABLED) {
                return current;
            }
            if (current.state() != CircuitBreakerState.HALF_OPEN) {
                break;
            }

            HalfOpenCallTracker.AdmissionResult admission = current.tracker().tryAcquire(
                    config.getPermittedCallsInHalfOpen(),
                    config.getMaxConcurrentCallsInHalfOpen()
            );
            if (admissionState != current) {
                // A transition detached this tracker. Retry against the new state.
                continue;
            }
            if (admission == HalfOpenCallTracker.AdmissionResult.ACCEPTED) {
                return current;
            }
            String reason = admission == HalfOpenCallTracker.AdmissionResult.TOTAL_LIMIT_REACHED
                    ? "Too many calls in HALF_OPEN"
                    : "Too many concurrent calls in HALF_OPEN";
            CallNotPermittedException rejection = new CallNotPermittedException(reason);
            statisticsCollector.recordRejectedCall();
            eventPublisher.publishRejected(CircuitBreakerState.HALF_OPEN, reason);
            throw rejection;
        }

        long callStateVersion = 0;
        CircuitBreakerState stateBeforePermission;
        CircuitBreakerState stateAfterPermission;
        CallNotPermittedException rejection = null;
        synchronized (slidingWindow) {
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
        return new CallPermission(callStateVersion, stateAfterPermission, halfOpenCallTracker);
    }

    private void transitionManually(CircuitBreakerState targetState) {
        CircuitBreakerState previousState;
        synchronized (slidingWindow) {
            previousState = state;
            if (previousState == targetState) {
                return;
            }
            changeState(targetState, targetState.resetsSlidingWindowOnManualTransition());
        }
        eventPublisher.publishStateTransition(previousState, targetState);
    }

    private void processSuccess(long callStateVersion, long startSecond, int startNano) {
        Duration callDuration = getCallDuration(startSecond, startNano);
        boolean slowCall = isSlowCall(callDuration);
        statisticsCollector.recordCall(callDuration, false);
        CallPermission current = admissionState;
        if (!slowCall && current.stateVersion() == callStateVersion
                && current.state() == CircuitBreakerState.CLOSED
                && slidingWindow.tryRecordHealthySuccess()) {
            eventPublisher.publishSuccess(callDuration, false);
            return;
        }
        CircuitBreakerState stateBeforeCompletion;
        CircuitBreakerState stateAfterCompletion;
        synchronized (slidingWindow) {
            stateBeforeCompletion = state;
            onSuccess(callStateVersion, slowCall);
            stateAfterCompletion = state;
        }

        eventPublisher.publishSuccess(callDuration, slowCall);
        eventPublisher.publishStateTransition(stateBeforeCompletion, stateAfterCompletion);
    }

    private void processException(
            long callStateVersion,
            long startSecond,
            int startNano,
            Throwable exception) {
        Duration callDuration = getCallDuration(startSecond, startNano);
        boolean slowCall = isSlowCall(callDuration);
        boolean shouldRecordException = config.shouldRecordException(exception);
        statisticsCollector.recordCall(callDuration, shouldRecordException);
        CircuitBreakerState stateBeforeCompletion;
        CircuitBreakerState stateAfterCompletion;
        synchronized (slidingWindow) {
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
            if (slidingWindow.recordAndCheckThresholdLocked(false, slowCall,
                    config.getMinimumCalls(), config.getFailureRateThreshold(),
                    config.getSlowCallRateThreshold())) {
                transitionToOpen();
            }
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
            if (slidingWindow.recordAndCheckThresholdLocked(true, slowCall,
                    config.getMinimumCalls(), config.getFailureRateThreshold(),
                    config.getSlowCallRateThreshold())) {
                transitionToOpen();
            }
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

    private Duration getCallDuration(long startSecond, int startNano) {
        Instant completedAt = clock.instant();
        Duration callDuration = Duration.ofSeconds(
                completedAt.getEpochSecond() - startSecond,
                completedAt.getNano() - startNano
        );
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
        halfOpenCallTracker = new HalfOpenCallTracker();
        if (resetSlidingWindow) {
            slidingWindow.reset();
        }
        // Publish only after the new state's bookkeeping is ready for callers.
        admissionState = new CallPermission(stateVersion, targetState, halfOpenCallTracker);
    }

    private record CallPermission(long stateVersion, CircuitBreakerState state,
                                  HalfOpenCallTracker tracker) {
        private boolean ignoreResults() {
            return !state.recordsResults();
        }
    }

}
