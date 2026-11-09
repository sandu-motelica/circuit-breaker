package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;

import java.time.Duration;
import java.util.Random;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureRateThreshold(0.5f)
                .slidingWindowSize(5)
                .minimumCalls(4)
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .durationInOpenState(Duration.ofSeconds(3))
                .permittedCallsInHalfOpen(2)
                .build();

        CircuitBreaker cb = new CircuitBreaker(config);

        Random random = new Random();

        for (int i = 1; i <= 30; i++) {
            System.out.println("\n--- Call #" + i + " ---");

            try {
                String result = cb.execute(() -> {
                    if (random.nextBoolean()) {
                        throw new RuntimeException("Simulated failure");
                    }
                    return "OK";
                });

                System.out.println("Result: " + result);

            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
            }

            System.out.println("Failure rate: " + cb.getFailureRate());
            System.out.println("Resulting State: " + cb.getState());

            Thread.sleep(500);
        }
    }
}
