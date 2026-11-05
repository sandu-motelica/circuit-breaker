package com.sm.cb.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountBasedSlidingWindowTest {

    @Test
    void evictOldestOutcomeWhenWindowIsFull() {
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(3);
        window.recordFailure();
        window.recordSuccess();
        window.recordSuccess();

        assertEquals(1.0f / 3.0f, window.getFailureRate());

        window.recordSuccess();

        assertEquals(0.0f, window.getFailureRate());
        assertEquals(3, window.getTotalCalls());
    }
}
