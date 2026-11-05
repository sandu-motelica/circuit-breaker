package com.sm.cb.window;

import com.sm.cb.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeBasedSlidingWindowTest {

    @Test
    void expireCallsAtWindowBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(2, clock);
        window.recordFailure();

        clock.advance(Duration.ofSeconds(1));
        assertEquals(1, window.getTotalCalls());
        assertEquals(1.0f, window.getFailureRate());

        clock.advance(Duration.ofSeconds(1));
        assertEquals(0, window.getTotalCalls());
        assertEquals(0.0f, window.getFailureRate());
    }
}
