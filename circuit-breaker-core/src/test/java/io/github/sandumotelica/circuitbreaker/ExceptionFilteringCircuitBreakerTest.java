package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionFilteringCircuitBreakerTest {
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void ignoreConfiguredExceptionWithoutAffectingFailureRate() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);

        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            throw new UserValidationException();
        }));

        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0.0f, circuitBreaker.getFailureRate());

        executeServiceFailure(circuitBreaker);
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(1.0f, circuitBreaker.getFailureRate());

        executeServiceFailure(circuitBreaker);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void recordOnlyConfiguredExceptionTypesIncludingSubclasses() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .recordExceptions(ExternalServiceException.class)
                .build(), clock);

        assertThrows(IllegalStateException.class, () -> circuitBreaker.execute(() -> {
            throw new IllegalStateException("Not an external service failure");
        }));
        assertEquals(0.0f, circuitBreaker.getFailureRate());

        assertThrows(SpecificServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new SpecificServiceException();
        }));
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(1.0f, circuitBreaker.getFailureRate());

        executeServiceFailure(circuitBreaker);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void ignoredExceptionTakesPriorityOverRecordedException() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .recordExceptions(RuntimeException.class)
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);

        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            throw new UserValidationException();
        }));

        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0.0f, circuitBreaker.getFailureRate());
    }

    @Test
    void ignoredExceptionReleasesHalfOpenPermission() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .durationInOpenState(Duration.ofSeconds(1))
                .permittedCallsInHalfOpen(1)
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);

        executeServiceFailure(circuitBreaker);
        executeServiceFailure(circuitBreaker);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        clock.advance(Duration.ofSeconds(1));
        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            throw new UserValidationException();
        }));
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.execute(() -> "Probe OK");
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    private CircuitBreakerConfig.Builder baseConfig() {
        return CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slidingWindowSize(2)
                .minimumCalls(2)
                .slidingWindowType(SlidingWindowType.COUNT_BASED);
    }

    private void executeServiceFailure(CircuitBreaker circuitBreaker) {
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
    }

    private static class ExternalServiceException extends RuntimeException {
    }

    private static class SpecificServiceException extends ExternalServiceException {
    }

    private static class UserValidationException extends RuntimeException {
    }
}
