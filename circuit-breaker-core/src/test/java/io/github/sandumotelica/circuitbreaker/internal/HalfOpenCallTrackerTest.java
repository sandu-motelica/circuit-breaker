package io.github.sandumotelica.circuitbreaker.internal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalfOpenCallTrackerTest {

    @Test
    void enforceConcurrentAndTotalCallLimitsIndependently() {
        HalfOpenCallTracker tracker = new HalfOpenCallTracker();

        assertEquals(HalfOpenCallTracker.AdmissionResult.ACCEPTED, tracker.tryAcquire(3, 2));
        assertEquals(HalfOpenCallTracker.AdmissionResult.ACCEPTED, tracker.tryAcquire(3, 2));
        assertEquals(HalfOpenCallTracker.AdmissionResult.CONCURRENT_LIMIT_REACHED,
                tracker.tryAcquire(3, 2));

        tracker.recordCompletion(false, false);
        assertEquals(HalfOpenCallTracker.AdmissionResult.ACCEPTED, tracker.tryAcquire(3, 2));
        assertEquals(HalfOpenCallTracker.AdmissionResult.TOTAL_LIMIT_REACHED,
                tracker.tryAcquire(3, 2));
    }

    @Test
    void calculateRatesFromCompletedCalls() {
        HalfOpenCallTracker tracker = new HalfOpenCallTracker();
        tracker.tryAcquire(2, 2);
        tracker.tryAcquire(2, 2);

        tracker.recordCompletion(true, true);
        assertFalse(tracker.hasCompleted(2));
        tracker.recordCompletion(false, false);

        assertTrue(tracker.hasCompleted(2));
        assertEquals(0.5f, tracker.failureRate());
        assertEquals(0.5f, tracker.slowCallRate());
    }

    @Test
    void ignoredCallReleasesItsPermission() {
        HalfOpenCallTracker tracker = new HalfOpenCallTracker();
        tracker.tryAcquire(1, 1);

        tracker.recordIgnoredCall();

        assertEquals(HalfOpenCallTracker.AdmissionResult.ACCEPTED, tracker.tryAcquire(1, 1));
    }
}
