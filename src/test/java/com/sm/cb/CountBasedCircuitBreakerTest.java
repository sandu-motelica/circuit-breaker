package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CountBasedCircuitBreakerTest {
    private CircuitBreakerConfig config;
    private CircuitBreaker cb;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        this.config = new CircuitBreakerConfig(
                0.5f,
                5,
                4,
                SlidingWindowType.COUNT_BASED,
                Duration.ofMillis(200),
                2
        );
        this.clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        this.cb = new CircuitBreaker(config, clock);
    }

    @Test
    void startInClosedState() {
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertEquals(0.0f, cb.getFailureRate());
    }

    @Test
    void notOpenIfMinimumCallsNotReach() {
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        assertEquals(CircuitBreakerState.CLOSED, cb.getState(), "Circuit shouldn't be OPEN under 4 calls");
        assertEquals(1.0f, cb.getFailureRate());
    }

    @Test
    void OpenWhenFailureRateExceedsThreshold() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        assertEquals(CircuitBreakerState.OPEN, cb.getState(), "The result should be OPEN after 4 failures");

        CallNotPermittedException exception = assertThrows(CallNotPermittedException.class, () -> cb.execute(() -> "Never executed"));
        assertEquals("Circuit breaker is OPEN", exception.getMessage());
    }

    @Test
    void transitionToHalfOpenAndThenClosedOnSuccess() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        clock.advance(Duration.ofMillis(200));
        cb.execute(() -> "Probe 1 OK");

        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState(), "Should transition to HALF_OPEN after timeout");

        cb.execute(() -> "Probe 2 OK");

        assertEquals(CircuitBreakerState.CLOSED, cb.getState(), "Should transition to CLOSED after successful probes");
    }

    @Test
    void transitionToOpenIfProbeFailsInHalfOpen() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        clock.advance(Duration.ofMillis(200));

        cb.execute(() -> "Probe 1 OK");
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

        assertThrows(RuntimeException.class, () -> cb.execute(() -> {
            throw new RuntimeException("Simulated probe error");
        }));

        assertEquals(CircuitBreakerState.OPEN, cb.getState(), "Should instantly revert to OPEN if a probe fails");
    }

    @Test
    void remainOpenUntilEntireWaitDurationPasses() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        clock.advance(Duration.ofMillis(199));
        assertThrows(CallNotPermittedException.class, () -> cb.execute(() -> "Too early"));
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        clock.advance(Duration.ofMillis(1));
        cb.execute(() -> "Probe OK");
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());
    }
}
