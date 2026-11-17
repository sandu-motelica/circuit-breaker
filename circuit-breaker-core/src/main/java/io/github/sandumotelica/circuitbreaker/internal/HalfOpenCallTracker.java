package io.github.sandumotelica.circuitbreaker.internal;

import java.util.concurrent.atomic.AtomicLong;

public final class HalfOpenCallTracker {
    public enum AdmissionResult {
        ACCEPTED,
        TOTAL_LIMIT_REACHED,
        CONCURRENT_LIMIT_REACHED
    }

    private final AtomicLong acceptedAndActive = new AtomicLong();
    private int completedCalls;
    private int failureCount;
    private int slowCallCount;

    public AdmissionResult tryAcquire(int totalCallLimit, int concurrentCallLimit) {
        while (true) {
            long current = acceptedAndActive.get();
            int accepted = (int) (current >>> 32);
            int active = (int) current;
            if (accepted >= totalCallLimit) {
                return AdmissionResult.TOTAL_LIMIT_REACHED;
            }
            if (active >= concurrentCallLimit) {
                return AdmissionResult.CONCURRENT_LIMIT_REACHED;
            }
            long updated = ((long) (accepted + 1) << 32) | ((active + 1) & 0xffffffffL);
            if (acceptedAndActive.compareAndSet(current, updated)) {
                return AdmissionResult.ACCEPTED;
            }
        }
    }

    public void recordCompletion(boolean failure, boolean slowCall) {
        release(false);
        completedCalls++;

        if (failure) {
            failureCount++;
        }
        if (slowCall) {
            slowCallCount++;
        }
    }

    public void recordIgnoredCall() {
        release(true);
    }

    public boolean hasCompleted(int requiredCalls) {
        return completedCalls >= requiredCalls;
    }

    public int failureCount() {
        return failureCount;
    }

    public int slowCallCount() {
        return slowCallCount;
    }

    public float failureRate() {
        return rateOf(failureCount);
    }

    public float slowCallRate() {
        return rateOf(slowCallCount);
    }

    private void release(boolean ignored) {
        while (true) {
            long current = acceptedAndActive.get();
            int accepted = (int) (current >>> 32);
            int active = (int) current;
            long updated = ((long) (accepted - (ignored ? 1 : 0)) << 32)
                    | ((active - 1) & 0xffffffffL);
            if (acceptedAndActive.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    private float rateOf(int calls) {
        if (completedCalls == 0) {
            return 0.0f;
        }
        return (float) calls / completedCalls;
    }
}
