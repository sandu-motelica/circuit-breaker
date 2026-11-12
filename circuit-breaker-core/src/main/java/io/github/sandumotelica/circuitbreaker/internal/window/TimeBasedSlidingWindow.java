package io.github.sandumotelica.circuitbreaker.internal.window;

import java.time.Clock;
import java.util.Objects;

public class TimeBasedSlidingWindow implements SlidingWindow {
    private static class SecondBucket {
        long second;
        int totalCalls;
        int failureCount;
        int slowCallCount;

        public SecondBucket() {
            this.reset();
        }

        void reset() {
            this.second = -1;
            this.failureCount = 0;
            this.slowCallCount = 0;
            this.totalCalls = 0;
        }
    }

    private final SecondBucket[] buckets;
    private final int windowSizeInSeconds;
    private final Clock clock;

    public TimeBasedSlidingWindow(int windowSizeInSeconds) {
        this(windowSizeInSeconds, Clock.systemUTC());
    }

    public TimeBasedSlidingWindow(int windowSizeInSeconds, Clock clock) {
        if (windowSizeInSeconds <= 0) throw new IllegalArgumentException("Window size must be greater than 0s");
        this.windowSizeInSeconds = windowSizeInSeconds;
        this.buckets = new SecondBucket[windowSizeInSeconds];
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");

        for (int i = 0; i < windowSizeInSeconds; i++) {
            buckets[i] = new SecondBucket();
        }
    }

    @Override
    public synchronized void recordSuccess(boolean slow) {
        record(false, slow);
    }

    @Override
    public synchronized void recordFailure(boolean slow) {
        record(true, slow);
    }

    @Override
    public synchronized SlidingWindowMetrics getMetrics() {
        long nowSecond = currentSecond();
        int totalFailures = 0;
        int totalSlowCalls = 0;
        int totalCalls = 0;
        for (int i = 0; i < windowSizeInSeconds; i++) {
            SecondBucket bucket = buckets[i];
            if (bucket.second >= 0 && bucket.second > nowSecond - windowSizeInSeconds) {
                totalFailures += bucket.failureCount;
                totalSlowCalls += bucket.slowCallCount;
                totalCalls += bucket.totalCalls;
            }
        }
        return new SlidingWindowMetrics(totalCalls, totalFailures, totalSlowCalls);
    }

    @Override
    public synchronized void reset() {
        for (int i = 0; i < windowSizeInSeconds; i++) {
            buckets[i].reset();
        }
    }

    private void record(boolean failure, boolean slowCall) {
        long nowSecond = currentSecond();

        int index = (int) (nowSecond % windowSizeInSeconds);

        SecondBucket bucket = buckets[index];

        if (bucket.second != nowSecond) {
            bucket.second = nowSecond;
            bucket.totalCalls = 0;
            bucket.failureCount = 0;
            bucket.slowCallCount = 0;
        }

        bucket.totalCalls++;

        if (failure) {
            bucket.failureCount++;
        }
        if (slowCall) {
            bucket.slowCallCount++;
        }
    }

    private long currentSecond() {
        return clock.instant().getEpochSecond();
    }
}
