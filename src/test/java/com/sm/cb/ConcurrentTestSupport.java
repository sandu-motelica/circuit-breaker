package com.sm.cb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentTestSupport {
    private static final int THREAD_COUNT = 16;

    private ConcurrentTestSupport() {
    }

    public static void runConcurrently(int taskCount, Runnable task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    await(startGate);
                    task.run();
                }));
            }

            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    public static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting in concurrent test", e);
        }
    }
}
