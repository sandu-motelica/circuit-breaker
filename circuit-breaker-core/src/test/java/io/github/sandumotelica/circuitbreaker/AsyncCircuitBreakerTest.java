package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.CallNotPermittedException;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEvent;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

class AsyncCircuitBreakerTest {
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void recordSuccessOnlyAfterFutureCompletes() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig().build(), clock);
        CompletableFuture<String> operation = new CompletableFuture<>();
        List<CircuitBreakerEvent> events = new ArrayList<>();
        circuitBreaker.getEventPublisher().addListener(events::add);

        CompletableFuture<String> result = circuitBreaker.executeAsync(() -> operation);

        assertFalse(result.isDone());
        assertEquals(0, circuitBreaker.getStatistics().totalCalls());
        assertEquals(0, events.size());

        clock.advance(Duration.ofMillis(40));
        operation.complete("OK");

        assertEquals("OK", result.join());
        CircuitBreakerStatistics statistics = circuitBreaker.getStatistics();
        assertEquals(1, statistics.totalCalls());
        assertEquals(Duration.ofMillis(40), statistics.averageDuration());
        assertEquals(1, events.size());
        assertInstanceOf(CircuitBreakerEvent.CallSucceeded.class, events.getFirst());
    }

    @Test
    void recordFailureAndOpenOnlyAfterFutureCompletes() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(1)
                .minimumCalls(1)
                .failureRateThreshold(1.0f)
                .build(), clock);
        CompletableFuture<String> operation = new CompletableFuture<>();

        CompletableFuture<String> result = circuitBreaker.executeAsync(() -> operation);

        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        operation.completeExceptionally(new ExternalServiceException());

        CompletionException completionException =
                assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ExternalServiceException.class, completionException.getCause());
        assertEquals(1.0f, circuitBreaker.getFailureRate());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertEquals(1, circuitBreaker.getStatistics().failedCalls());
    }

    @Test
    void detectSlowAsyncCallUsingItsFullDuration() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(1)
                .minimumCalls(1)
                .slowCallRateThreshold(1.0f)
                .slowCallDurationThreshold(Duration.ofMillis(100))
                .build(), clock);
        CompletableFuture<String> operation = new CompletableFuture<>();

        circuitBreaker.executeAsync(() -> operation);
        clock.advance(Duration.ofMillis(100));
        operation.complete("Slow success");

        assertEquals(1.0f, circuitBreaker.getSlowCallRate());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void applyExceptionFilteringToCheckedAsyncException() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .ignoreExceptions(IOException.class)
                .build(), clock);
        CompletableFuture<String> operation = new CompletableFuture<>();

        CompletableFuture<String> result = circuitBreaker.executeAsync(() -> operation);
        operation.completeExceptionally(new IOException("Validation failed"));

        CompletionException completionException =
                assertThrows(CompletionException.class, result::join);
        assertInstanceOf(IOException.class, completionException.getCause());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
        assertEquals(0, circuitBreaker.getStatistics().failedCalls());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void returnFailedFutureWhenAsyncCallIsRejected() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(1)
                .minimumCalls(1)
                .failureRateThreshold(1.0f)
                .build(), clock);
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        AtomicBoolean supplierInvoked = new AtomicBoolean();

        CompletableFuture<String> rejected = circuitBreaker.executeAsync(() -> {
            supplierInvoked.set(true);
            return CompletableFuture.completedFuture("Must not run");
        });

        CompletionException completionException =
                assertThrows(CompletionException.class, rejected::join);
        assertInstanceOf(CallNotPermittedException.class, completionException.getCause());
        assertFalse(supplierInvoked.get());
        assertEquals(1, circuitBreaker.getStatistics().rejectedCalls());
    }

    @Test
    void keepHalfOpenPermissionUntilAsyncProbeCompletes() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(1)
                .minimumCalls(1)
                .failureRateThreshold(1.0f)
                .durationInOpenState(Duration.ofSeconds(1))
                .permittedCallsInHalfOpen(1)
                .maxConcurrentCallsInHalfOpen(1)
                .build(), clock);
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        clock.advance(Duration.ofSeconds(1));
        CompletableFuture<String> probe = new CompletableFuture<>();

        circuitBreaker.executeAsync(() -> probe);
        CompletableFuture<String> rejected = circuitBreaker.executeAsync(
                () -> CompletableFuture.completedFuture("Second probe")
        );

        CompletionException completionException =
                assertThrows(CompletionException.class, rejected::join);
        assertInstanceOf(CallNotPermittedException.class, completionException.getCause());
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        probe.complete("Recovered");

        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    private CircuitBreakerConfig.Builder baseConfig() {
        return CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slowCallRateThreshold(1.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .slidingWindowSize(2)
                .minimumCalls(2);
    }

    private static final class ExternalServiceException extends RuntimeException {
    }
}
