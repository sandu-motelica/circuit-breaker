package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlowCallCircuitBreakerTest {
    private static final Duration SLOW_CALL_THRESHOLD = Duration.ofMillis(100);

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void openWhenSlowCallRateReachesThreshold() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(4)
                .minimumCalls(4)
                .build(), clock);

        executeSlowSuccess(circuitBreaker);
        circuitBreaker.execute(() -> "Fast call");
        executeSlowSuccess(circuitBreaker);
        circuitBreaker.execute(() -> "Fast call");

        assertEquals(0.5f, circuitBreaker.getSlowCallRate());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void keepClosedWhenSlowCallRateIsBelowThreshold() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(4)
                .minimumCalls(4)
                .build(), clock);

        executeSlowSuccess(circuitBreaker);
        circuitBreaker.execute(() -> "Fast call 1");
        circuitBreaker.execute(() -> "Fast call 2");
        circuitBreaker.execute(() -> "Fast call 3");

        assertEquals(0.25f, circuitBreaker.getSlowCallRate());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void measureFailedCallsAndExcludeIgnoredExceptions() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(2)
                .minimumCalls(2)
                .failureRateThreshold(1.0f)
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);

        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            clock.advance(SLOW_CALL_THRESHOLD);
            throw new UserValidationException();
        }));
        assertEquals(0.0f, circuitBreaker.getSlowCallRate());

        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            clock.advance(SLOW_CALL_THRESHOLD);
            throw new ExternalServiceException();
        }));
        circuitBreaker.execute(() -> "Fast call");

        assertEquals(0.5f, circuitBreaker.getFailureRate());
        assertEquals(0.5f, circuitBreaker.getSlowCallRate());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void reopenWhenHalfOpenProbesAreTooSlow() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .slidingWindowSize(2)
                .minimumCalls(2)
                .failureRateThreshold(1.0f)
                .durationInOpenState(Duration.ofSeconds(1))
                .permittedCallsInHalfOpen(2)
                .build(), clock);

        executeFailure(circuitBreaker);
        executeFailure(circuitBreaker);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        clock.advance(Duration.ofSeconds(1));
        executeSlowSuccess(circuitBreaker);
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.execute(() -> "Fast probe");
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    private CircuitBreakerConfig.Builder baseConfig() {
        return CircuitBreakerConfig.builder()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slowCallDurationThreshold(SLOW_CALL_THRESHOLD)
                .slowCallRateThreshold(0.5f);
    }

    private void executeSlowSuccess(CircuitBreaker circuitBreaker) {
        circuitBreaker.execute(() -> {
            clock.advance(SLOW_CALL_THRESHOLD);
            return "Slow call";
        });
    }

    private void executeFailure(CircuitBreaker circuitBreaker) {
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
    }

    private static class ExternalServiceException extends RuntimeException {
    }

    private static class UserValidationException extends RuntimeException {
    }
}
