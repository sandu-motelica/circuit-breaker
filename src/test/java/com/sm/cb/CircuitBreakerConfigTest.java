package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerConfigTest {

    @Test
    void rejectNullSlidingWindowType() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreakerConfig(
                0.5f, 5, 4, null, Duration.ofSeconds(1), 2
        ));
    }

    @Test
    void rejectMinimumCallsGreaterThanCountBasedWindow() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreakerConfig(
                0.5f, 5, 6, SlidingWindowType.COUNT_BASED, Duration.ofSeconds(1), 2
        ));
    }

    @Test
    void allowMinimumCallsGreaterThanTimeWindowSize() {
        assertDoesNotThrow(() -> new CircuitBreakerConfig(
                0.5f, 2, 10, SlidingWindowType.TIME_BASED, Duration.ofSeconds(1), 2
        ));
    }
}
