package com.sm.cb;

final class HalfOpenCallTracker {
    enum AdmissionResult {
        ACCEPTED,
        TOTAL_LIMIT_REACHED,
        CONCURRENT_LIMIT_REACHED
    }

    private int acceptedCalls;
    private int activeCalls;
    private int completedCalls;
    private int failureCount;
    private int slowCallCount;

    AdmissionResult tryAcquire(int totalCallLimit, int concurrentCallLimit) {
        if (acceptedCalls >= totalCallLimit) {
            return AdmissionResult.TOTAL_LIMIT_REACHED;
        }
        if (activeCalls >= concurrentCallLimit) {
            return AdmissionResult.CONCURRENT_LIMIT_REACHED;
        }

        acceptedCalls++;
        activeCalls++;
        return AdmissionResult.ACCEPTED;
    }

    void recordCompletion(boolean failure, boolean slowCall) {
        activeCalls--;
        completedCalls++;

        if (failure) {
            failureCount++;
        }
        if (slowCall) {
            slowCallCount++;
        }
    }

    void recordIgnoredCall() {
        acceptedCalls--;
        activeCalls--;
    }

    boolean hasCompleted(int requiredCalls) {
        return completedCalls >= requiredCalls;
    }

    int failureCount() {
        return failureCount;
    }

    int slowCallCount() {
        return slowCallCount;
    }

    float failureRate() {
        return rateOf(failureCount);
    }

    float slowCallRate() {
        return rateOf(slowCallCount);
    }

    void reset() {
        acceptedCalls = 0;
        activeCalls = 0;
        completedCalls = 0;
        failureCount = 0;
        slowCallCount = 0;
    }

    private float rateOf(int calls) {
        if (completedCalls == 0) {
            return 0.0f;
        }
        return (float) calls / completedCalls;
    }
}
