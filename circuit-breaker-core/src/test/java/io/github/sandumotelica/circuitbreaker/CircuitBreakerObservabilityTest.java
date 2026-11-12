package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.internal.DefaultCircuitBreakerEventPublisher;
import io.github.sandumotelica.circuitbreaker.CallNotPermittedException;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerEvent;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;
import io.github.sandumotelica.circuitbreaker.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerObservabilityTest {
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void publishCallTransitionSlowAndRejectionEvents() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig().build(), clock);
        List<CircuitBreakerEvent> events = new ArrayList<>();
        circuitBreaker.getEventPublisher().addListener(events::add);

        circuitBreaker.execute(() -> {
            clock.advance(Duration.ofMillis(20));
            return "OK";
        });
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            clock.advance(Duration.ofMillis(100));
            throw new ExternalServiceException();
        }));
        assertThrows(CallNotPermittedException.class,
                () -> circuitBreaker.execute(() -> "Rejected"));

        CircuitBreakerEvent.CallSucceeded succeeded = assertInstanceOf(
                CircuitBreakerEvent.CallSucceeded.class,
                events.get(0)
        );
        assertEquals(Duration.ofMillis(20), succeeded.duration());

        CircuitBreakerEvent.CallFailed failed = assertInstanceOf(
                CircuitBreakerEvent.CallFailed.class,
                events.get(1)
        );
        assertEquals(Duration.ofMillis(100), failed.duration());
        assertInstanceOf(ExternalServiceException.class, failed.exception());

        CircuitBreakerEvent.SlowCall slowCall = assertInstanceOf(
                CircuitBreakerEvent.SlowCall.class,
                events.get(2)
        );
        assertEquals(CircuitBreakerEvent.CallOutcome.FAILURE, slowCall.outcome());

        CircuitBreakerEvent.StateTransition transition = assertInstanceOf(
                CircuitBreakerEvent.StateTransition.class,
                events.get(3)
        );
        assertEquals(CircuitBreakerState.CLOSED, transition.from());
        assertEquals(CircuitBreakerState.OPEN, transition.to());

        assertInstanceOf(CircuitBreakerEvent.CallRejected.class, events.get(4));
        assertEquals(5, events.size());
    }

    @Test
    void exposeCumulativeStatistics() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);

        circuitBreaker.execute(() -> {
            clock.advance(Duration.ofMillis(20));
            return "OK";
        });
        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            clock.advance(Duration.ofMillis(40));
            throw new UserValidationException();
        }));
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            clock.advance(Duration.ofMillis(60));
            throw new ExternalServiceException();
        }));
        assertThrows(CallNotPermittedException.class,
                () -> circuitBreaker.execute(() -> "Rejected"));

        CircuitBreakerStatistics statistics = circuitBreaker.getStatistics();
        assertEquals(3, statistics.totalCalls());
        assertEquals(1, statistics.failedCalls());
        assertEquals(1, statistics.rejectedCalls());
        assertEquals(Duration.ofMillis(40), statistics.averageDuration());
    }

    @Test
    void publishIgnoredCallAndIsolateListenerFailures() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .ignoreExceptions(UserValidationException.class)
                .build(), clock);
        List<CircuitBreakerEvent> events = new ArrayList<>();
        circuitBreaker.getEventPublisher().addListener(event -> {
            throw new RuntimeException("Broken listener");
        });
        circuitBreaker.getEventPublisher().addListener(events::add);

        assertThrows(UserValidationException.class, () -> circuitBreaker.execute(() -> {
            throw new UserValidationException();
        }));

        assertEquals(1, events.size());
        assertInstanceOf(CircuitBreakerEvent.CallIgnored.class, events.get(0));
    }

    @Test
    void publishBothTransitionsWhenRecoveringThroughHalfOpen() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(baseConfig()
                .durationInOpenState(Duration.ofSeconds(1))
                .permittedCallsInHalfOpen(1)
                .build(), clock);
        List<CircuitBreakerEvent.StateTransition> transitions = new ArrayList<>();
        circuitBreaker.getEventPublisher().addListener(event -> {
            if (event instanceof CircuitBreakerEvent.StateTransition transition) {
                transitions.add(transition);
            }
        });

        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));
        assertThrows(ExternalServiceException.class, () -> circuitBreaker.execute(() -> {
            throw new ExternalServiceException();
        }));

        clock.advance(Duration.ofSeconds(1));
        circuitBreaker.execute(() -> "Probe OK");

        assertEquals(3, transitions.size());
        assertEquals(CircuitBreakerState.CLOSED, transitions.get(0).from());
        assertEquals(CircuitBreakerState.OPEN, transitions.get(0).to());
        assertEquals(CircuitBreakerState.OPEN, transitions.get(1).from());
        assertEquals(CircuitBreakerState.HALF_OPEN, transitions.get(1).to());
        assertEquals(CircuitBreakerState.HALF_OPEN, transitions.get(2).from());
        assertEquals(CircuitBreakerState.CLOSED, transitions.get(2).to());
    }

    @Test
    void avoidCreatingEventTimestampsWithoutListeners() {
        AtomicInteger clockReads = new AtomicInteger();
        Clock trackingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                clockReads.incrementAndGet();
                return Instant.EPOCH;
            }
        };
        DefaultCircuitBreakerEventPublisher publisher =
                new DefaultCircuitBreakerEventPublisher(trackingClock);

        publisher.publishSuccess(Duration.ZERO, true);
        publisher.publishFailure(Duration.ZERO, new ExternalServiceException(), true);
        publisher.publishIgnored(Duration.ZERO, new UserValidationException());
        publisher.publishRejected(CircuitBreakerState.OPEN, "OPEN");
        publisher.publishStateTransition(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN);

        assertEquals(0, clockReads.get());
    }

    private CircuitBreakerConfig.Builder baseConfig() {
        return CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slowCallRateThreshold(1.0f)
                .slowCallDurationThreshold(Duration.ofMillis(100))
                .slidingWindowSize(2)
                .minimumCalls(2)
                .slidingWindowType(SlidingWindowType.COUNT_BASED);
    }

    private static class ExternalServiceException extends RuntimeException {
    }

    private static class UserValidationException extends RuntimeException {
    }
}
