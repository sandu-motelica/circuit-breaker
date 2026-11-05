package com.sm.cb.window;

import java.time.Clock;
import java.util.Objects;

public class TimeBasedSlidingWindow implements SlidingWindow {
    private static class SecondBucket {
        long second;
        int totalCalls;
        int failures;

        public SecondBucket() {
            this.reset();
        }

        void reset() {
            this.second = -1;
            this.failures = 0;
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
    public synchronized void recordSuccess() {
        record(true);
    }

    @Override
    public synchronized void recordFailure() {
        record(false);
    }

    @Override
    public synchronized float getFailureRate() {
        long nowSecond = currentSecond();
        int totalFailures = 0, totalCalls = 0;
        for (int i = 0; i < windowSizeInSeconds; i++) {
            SecondBucket bucket = buckets[i];
            if (bucket.second >= 0 && bucket.second > nowSecond - windowSizeInSeconds) {
                totalFailures += bucket.failures;
                totalCalls += bucket.totalCalls;
            }
        }
        if (totalCalls == 0) {
            return 0.0f;
        }
        return (float) totalFailures / totalCalls;
    }

    @Override
    public synchronized void reset() {
        for (int i = 0; i < windowSizeInSeconds; i++) {
            buckets[i].reset();
        }
    }

    @Override
    public synchronized int getTotalCalls() {
        long nowSecond = currentSecond();
        int total = 0;

        for (int i = 0; i < windowSizeInSeconds; i++) {
            SecondBucket bucket = buckets[i];
            if (bucket.second >= 0 && bucket.second > nowSecond - windowSizeInSeconds) {
                total += bucket.totalCalls;
            }
        }
        return total;
    }

    private void record(boolean success) {
        long nowSecond = currentSecond();

        int index = (int) (nowSecond % windowSizeInSeconds);

        SecondBucket bucket = buckets[index];

        if (bucket.second != nowSecond) {
            bucket.second = nowSecond;
            bucket.totalCalls = 0;
            bucket.failures = 0;
        }

        bucket.totalCalls++;

        if (!success) {
            bucket.failures++;
        }
    }

    private long currentSecond() {
        return clock.instant().getEpochSecond();
    }
}
