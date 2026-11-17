package io.github.sandumotelica.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentCircuitBreakerTest {
    @Test
    void concurrentSuccessfulCallsPreserveWindowAndStatistics() throws Exception {
        int calls = 1_000;
        CircuitBreaker breaker = new CircuitBreaker(CircuitBreakerConfig.builder()
                .slidingWindowSize(calls)
                .minimumCalls(calls)
                .build());

        ConcurrentTestSupport.runConcurrently(calls, () -> breaker.execute(() -> "ok"));

        assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        assertEquals(calls, breaker.getStatistics().totalCalls());
        assertEquals(0, breaker.getStatistics().failedCalls());
        assertEquals(0.0f, breaker.getFailureRate());
    }

    @Test
    void concurrentFailuresOpenOnceAndRejectLaterCalls() throws Exception {
        AtomicInteger invoked = new AtomicInteger();
        CircuitBreaker breaker = new CircuitBreaker(CircuitBreakerConfig.builder()
                .slidingWindowSize(1_000)
                .minimumCalls(16)
                .failureRateThreshold(1.0f)
                .durationInOpenState(Duration.ofDays(1))
                .build());

        ConcurrentTestSupport.runConcurrently(200, () -> {
            try {
                breaker.execute(() -> {
                    invoked.incrementAndGet();
                    throw new IllegalStateException("failure");
                });
            } catch (IllegalStateException | CallNotPermittedException ignored) {
                // Both outcomes are expected around the transition to OPEN.
            }
        });

        assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        assertEquals(invoked.get(), breaker.getStatistics().totalCalls());
        assertEquals(invoked.get(), breaker.getStatistics().failedCalls());
        assertEquals(200 - invoked.get(), breaker.getStatistics().rejectedCalls());
        assertThrows(CallNotPermittedException.class, () -> breaker.execute(() -> "unreachable"));
    }

    @Test
    void healthyWindowFastPathStillRecordsAConcurrentFailure() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(CircuitBreakerConfig.builder()
                .slidingWindowSize(64)
                .minimumCalls(64)
                .failureRateThreshold(0.01f)
                .durationInOpenState(Duration.ofDays(1))
                .build());
        for (int i = 0; i < 64; i++) {
            breaker.execute(() -> "ok");
        }

        AtomicInteger attempts = new AtomicInteger();
        ConcurrentTestSupport.runConcurrently(100, () -> {
            try {
                if (attempts.getAndIncrement() == 50) {
                    breaker.execute(() -> {
                        throw new IllegalStateException("failure");
                    });
                } else {
                    breaker.execute(() -> "ok");
                }
            } catch (IllegalStateException | CallNotPermittedException ignored) {
                // Admission and the OPEN transition may race.
            }
        });

        assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        assertTrue(breaker.getFailureRate() > 0);
    }
}
