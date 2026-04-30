package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimeBasedCircuitBreakerTest {

    private CircuitBreakerConfig config;
    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        this.config = new CircuitBreakerConfig(
                0.5f,
                2,
                4,
                SlidingWindowType.TIME_BASED,
                Duration.ofSeconds(3),
                2
        );
        this.cb = new CircuitBreaker(config);
    }

    @Test
    void shouldForgetOldCallsWhenTimePasses() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertEquals(1.0f, cb.getFailureRate(), "Failure rate should be 100% currently");
        Thread.sleep(2100);

        cb.execute(() -> "OK");

        assertEquals(0.0f, cb.getFailureRate(), "Failure rate should be 0.0, old calls have expired");
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
    }

    @Test
    void shouldOpenWhenThresholdReachedInSameTimeWindow() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        assertEquals(CircuitBreakerState.OPEN, cb.getState());
    }
}