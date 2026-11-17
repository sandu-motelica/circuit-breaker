package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimeBasedCircuitBreakerTest {

    private CircuitBreakerConfig config;
    private CircuitBreaker cb;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        this.config = CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slidingWindowSize(2)
                .minimumCalls(4)
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .durationInOpenState(Duration.ofSeconds(3))
                .permittedCallsInHalfOpen(2)
                .build();
        this.clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        this.cb = new CircuitBreaker(config, clock);
    }

    @Test
    void shouldForgetOldCallsWhenTimePasses() {
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }

        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertEquals(1.0f, cb.getFailureRate(), "Failure rate should be 100% currently");
        clock.advance(Duration.ofSeconds(2));

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

    @Test
    void preserveNanosecondDurationAcrossSecondBoundary() {
        MutableClock preciseClock = new MutableClock(Instant.parse("2026-01-01T00:00:00.999999900Z"));
        CircuitBreaker preciseBreaker = new CircuitBreaker(config.toBuilder()
                .minimumCalls(1)
                .slowCallDurationThreshold(Duration.ofNanos(300))
                .slowCallRateThreshold(1.0f)
                .build(), preciseClock);

        preciseBreaker.execute(() -> {
            preciseClock.advance(Duration.ofNanos(300));
            return "OK";
        });

        assertEquals(Duration.ofNanos(300), preciseBreaker.getStatistics().averageDuration());
        assertEquals(CircuitBreakerState.OPEN, preciseBreaker.getState());
    }

    @Test
    void clampDurationWhenClockMovesBackwards() {
        cb.execute(() -> {
            clock.advance(Duration.ofNanos(-1));
            return "OK";
        });

        assertEquals(Duration.ZERO, cb.getStatistics().averageDuration());
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
    }
}
