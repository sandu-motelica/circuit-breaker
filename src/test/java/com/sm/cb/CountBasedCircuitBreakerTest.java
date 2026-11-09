package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.window.SlidingWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CountBasedCircuitBreakerTest {
    private CircuitBreakerConfig config;
    private CircuitBreaker cb;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        this.config = CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slidingWindowSize(5)
                .minimumCalls(4)
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .durationInOpenState(Duration.ofMillis(200))
                .permittedCallsInHalfOpen(2)
                .build();
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

    @Test
    void allowOnlyConfiguredNumberOfConcurrentHalfOpenCalls() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                throw new RuntimeException("Simulated error");
            }));
        }
        clock.advance(Duration.ofMillis(200));

        int totalAttempts = 10;
        int permittedCalls = config.getPermittedCallsInHalfOpen();
        ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch probesEntered = new CountDownLatch(permittedCalls);
        CountDownLatch callsRejected = new CountDownLatch(totalAttempts - permittedCalls);
        CountDownLatch releaseProbes = new CountDownLatch(1);
        AtomicInteger executedProbes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < totalAttempts; i++) {
                futures.add(executor.submit(() -> {
                    ConcurrentTestSupport.await(startGate);
                    try {
                        cb.execute(() -> {
                            executedProbes.incrementAndGet();
                            probesEntered.countDown();
                            ConcurrentTestSupport.await(releaseProbes);
                            return "Probe OK";
                        });
                    } catch (CallNotPermittedException e) {
                        callsRejected.countDown();
                    }
                }));
            }

            startGate.countDown();
            assertTrue(probesEntered.await(2, TimeUnit.SECONDS), "Permitted probes did not start concurrently");
            assertTrue(callsRejected.await(2, TimeUnit.SECONDS), "Extra probes were not rejected");
            releaseProbes.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            releaseProbes.countDown();
            executor.shutdownNow();
        }

        assertEquals(permittedCalls, executedProbes.get());
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
    }

    @Test
    void ignoreResultFromCallStartedInPreviousState() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch oldCallStarted = new CountDownLatch(1);
        CountDownLatch releaseOldCall = new CountDownLatch(1);

        Future<String> oldCall = executor.submit(() -> cb.execute(() -> {
            oldCallStarted.countDown();
            ConcurrentTestSupport.await(releaseOldCall);
            return "Old call OK";
        }));

        try {
            assertTrue(oldCallStarted.await(2, TimeUnit.SECONDS));

            for (int i = 0; i < 4; i++) {
                assertThrows(RuntimeException.class, () -> cb.execute(() -> {
                    throw new RuntimeException("Simulated error");
                }));
            }
            clock.advance(Duration.ofMillis(200));

            cb.execute(() -> "Probe 1 OK");
            assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

            releaseOldCall.countDown();
            assertEquals("Old call OK", oldCall.get(2, TimeUnit.SECONDS));
            assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState(),
                    "An old CLOSED call must not count as a HALF_OPEN probe");

            cb.execute(() -> "Probe 2 OK");
            assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        } finally {
            releaseOldCall.countDown();
            executor.shutdownNow();
        }
    }
}
