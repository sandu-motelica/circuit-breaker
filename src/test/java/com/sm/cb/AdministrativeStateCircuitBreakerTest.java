package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.observability.CircuitBreakerEvent;
import com.sm.cb.observability.CircuitBreakerStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdministrativeStateCircuitBreakerTest {
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void forcedOpenRejectsSyncAndAsyncCallsUntilManuallyChanged() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig(), clock);
        AtomicBoolean supplierInvoked = new AtomicBoolean();
        circuitBreaker.transitionToForcedOpenState();

        CallNotPermittedException syncRejection = assertThrows(
                CallNotPermittedException.class,
                () -> circuitBreaker.execute(() -> {
                    supplierInvoked.set(true);
                    return "Must not run";
                })
        );
        CompletableFuture<String> asyncRejection = circuitBreaker.executeAsync(() -> {
            supplierInvoked.set(true);
            return CompletableFuture.completedFuture("Must not run");
        });

        CompletionException completionException =
                assertThrows(CompletionException.class, asyncRejection::join);
        assertInstanceOf(CallNotPermittedException.class, completionException.getCause());
        assertEquals("Circuit breaker is FORCED_OPEN", syncRejection.getMessage());
        assertFalse(supplierInvoked.get());
        assertEquals(2, circuitBreaker.getStatistics().rejectedCalls());

        clock.advance(Duration.ofDays(1));
        assertThrows(CallNotPermittedException.class,
                () -> circuitBreaker.execute(() -> "Still rejected"));
        assertEquals(CircuitBreakerState.FORCED_OPEN, circuitBreaker.getState());
    }

    @Test
    void disabledLetsCallsPassWithoutCollectingResults() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig(), clock);
        circuitBreaker.transitionToDisabledState();

        assertEquals("Sync success", circuitBreaker.execute(() -> "Sync success"));
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        assertEquals("Async success", circuitBreaker.executeAsync(
                () -> CompletableFuture.completedFuture("Async success")
        ).join());
        CompletableFuture<String> asyncFailure = circuitBreaker.executeAsync(
                () -> CompletableFuture.failedFuture(new ExternalServiceException())
        );
        CompletionException completionException =
                assertThrows(CompletionException.class, asyncFailure::join);
        assertInstanceOf(ExternalServiceException.class, completionException.getCause());

        CircuitBreakerStatistics statistics = circuitBreaker.getStatistics();
        assertEquals(0, statistics.totalCalls());
        assertEquals(0, statistics.failedCalls());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
        assertEquals(CircuitBreakerState.DISABLED, circuitBreaker.getState());
    }

    @Test
    void metricsOnlyCollectsResultsWithoutOpeningCircuit() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig(), clock);
        circuitBreaker.transitionToMetricsOnlyState();

        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        circuitBreaker.execute(() -> "Success");

        assertEquals(0.5f, circuitBreaker.getFailureRate());
        assertEquals(2, circuitBreaker.getStatistics().totalCalls());
        assertEquals(1, circuitBreaker.getStatistics().failedCalls());
        assertEquals(CircuitBreakerState.METRICS_ONLY, circuitBreaker.getState());
        assertEquals("Still permitted", circuitBreaker.execute(() -> "Still permitted"));
    }

    @Test
    void exposeAllManualTransitionsAndPublishTheirEvents() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig(), clock);
        List<CircuitBreakerEvent.StateTransition> transitions = new ArrayList<>();
        circuitBreaker.getEventPublisher().addListener(event -> {
            if (event instanceof CircuitBreakerEvent.StateTransition transition) {
                transitions.add(transition);
            }
        });

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        circuitBreaker.transitionToMetricsOnlyState();
        circuitBreaker.transitionToDisabledState();
        circuitBreaker.transitionToForcedOpenState();
        circuitBreaker.transitionToClosedState();

        assertEquals(6, transitions.size());
        assertEquals(CircuitBreakerState.CLOSED, transitions.get(0).from());
        assertEquals(CircuitBreakerState.OPEN, transitions.get(0).to());
        assertEquals(CircuitBreakerState.FORCED_OPEN, transitions.get(5).from());
        assertEquals(CircuitBreakerState.CLOSED, transitions.get(5).to());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void resetStateSlidingWindowAndCumulativeStatistics() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureRateThreshold(1.0f)
                .slidingWindowSize(1)
                .minimumCalls(1)
                .build();
        CircuitBreaker circuitBreaker = new CircuitBreaker(config, clock);
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        circuitBreaker.reset();

        CircuitBreakerStatistics statistics = circuitBreaker.getStatistics();
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
        assertEquals(0.0f, circuitBreaker.getSlowCallRate());
        assertEquals(0, statistics.totalCalls());
        assertEquals(0, statistics.failedCalls());
        assertEquals(0, statistics.rejectedCalls());
        assertEquals("Available", circuitBreaker.execute(() -> "Available"));
    }

    private CircuitBreakerConfig baseConfig() {
        return CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slidingWindowSize(2)
                .minimumCalls(2)
                .durationInOpenState(Duration.ofSeconds(1))
                .permittedCallsInHalfOpen(1)
                .build();
    }

    private static final class ExternalServiceException extends RuntimeException {
    }
}
