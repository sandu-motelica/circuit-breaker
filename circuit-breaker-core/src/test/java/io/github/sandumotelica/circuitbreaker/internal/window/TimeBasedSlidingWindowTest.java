package io.github.sandumotelica.circuitbreaker.internal.window;

import io.github.sandumotelica.circuitbreaker.ConcurrentTestSupport;
import io.github.sandumotelica.circuitbreaker.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeBasedSlidingWindowTest {

    @Test
    void expireCallsAtWindowBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(2, clock);
        window.recordFailure(true);

        clock.advance(Duration.ofSeconds(1));
        assertEquals(1, window.getTotalCalls());
        assertEquals(1.0f, window.getFailureRate());
        assertEquals(1.0f, window.getSlowCallRate());

        clock.advance(Duration.ofSeconds(1));
        assertEquals(0, window.getTotalCalls());
        assertEquals(0.0f, window.getFailureRate());
        assertEquals(0.0f, window.getSlowCallRate());
    }

    @Test
    void supportInstantsOutsideEpochMillisRange() {
        MutableClock clock = new MutableClock(Instant.MAX.minusSeconds(2));
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(2, clock);
        window.recordSuccess(false);

        assertEquals(1, window.getTotalCalls());
        clock.advance(Duration.ofSeconds(2));
        assertEquals(0, window.getTotalCalls());
    }

    @Test
    void recordConcurrentCallsInSameBucketWithoutLosingResults() throws Exception {
        int numberOfCalls = 1_000;
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(2, clock);

        ConcurrentTestSupport.runConcurrently(numberOfCalls, () -> window.recordFailure(true));

        assertEquals(numberOfCalls, window.getTotalCalls());
        assertEquals(1.0f, window.getFailureRate());
        assertEquals(1.0f, window.getSlowCallRate());
    }
}
