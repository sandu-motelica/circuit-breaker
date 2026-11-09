package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerConfigTest {

    @Test
    void buildWithDefaultValues() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder().build();

        assertEquals(0.5f, config.getFailureRateThreshold());
        assertEquals(100, config.getSlidingWindowSize());
        assertEquals(100, config.getMinimumCalls());
        assertEquals(SlidingWindowType.COUNT_BASED, config.getSlidingWindowType());
        assertEquals(Duration.ofSeconds(60), config.getDurationInOpenState());
        assertEquals(10, config.getPermittedCallsInHalfOpen());
        assertEquals(1.0f, config.getSlowCallRateThreshold());
        assertEquals(Duration.ofSeconds(60), config.getSlowCallDurationThreshold());
        assertEquals(List.of(RuntimeException.class), config.getRecordedExceptionTypes());
        assertEquals(List.of(), config.getIgnoredExceptionTypes());
    }

    @Test
    void overrideDefaultValues() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureRateThreshold(0.25f)
                .slidingWindowSize(20)
                .minimumCalls(10)
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .durationInOpenState(Duration.ofSeconds(5))
                .permittedCallsInHalfOpen(3)
                .slowCallRateThreshold(0.4f)
                .slowCallDurationThreshold(Duration.ofMillis(500))
                .recordExceptions(ExternalServiceException.class)
                .ignoreExceptions(UserValidationException.class)
                .build();

        assertEquals(0.25f, config.getFailureRateThreshold());
        assertEquals(20, config.getSlidingWindowSize());
        assertEquals(10, config.getMinimumCalls());
        assertEquals(SlidingWindowType.TIME_BASED, config.getSlidingWindowType());
        assertEquals(Duration.ofSeconds(5), config.getDurationInOpenState());
        assertEquals(3, config.getPermittedCallsInHalfOpen());
        assertEquals(0.4f, config.getSlowCallRateThreshold());
        assertEquals(Duration.ofMillis(500), config.getSlowCallDurationThreshold());
        assertEquals(List.of(ExternalServiceException.class), config.getRecordedExceptionTypes());
        assertEquals(List.of(UserValidationException.class), config.getIgnoredExceptionTypes());
    }

    @Test
    void rejectNullSlidingWindowType() {
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .slidingWindowType(null)
                .build());
    }

    @Test
    void rejectMinimumCallsGreaterThanCountBasedWindow() {
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .slidingWindowSize(5)
                .minimumCalls(6)
                .build());
    }

    @Test
    void allowMinimumCallsGreaterThanTimeWindowSize() {
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
                .slidingWindowSize(2)
                .minimumCalls(10)
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .build());
    }

    @Test
    void rejectNullExceptionType() {
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .ignoreExceptions(UserValidationException.class, null));
    }

    @Test
    void rejectInvalidSlowCallConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .slowCallRateThreshold(Float.NaN)
                .build());
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .slowCallRateThreshold(1.1f)
                .build());
        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.builder()
                .slowCallDurationThreshold(Duration.ZERO)
                .build());
    }

    private static class ExternalServiceException extends RuntimeException {
    }

    private static class UserValidationException extends RuntimeException {
    }
}
