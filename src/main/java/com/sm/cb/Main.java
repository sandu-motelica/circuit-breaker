package com.sm.cb;

import com.sm.cb.window.SlidingWindowType;

import java.time.Duration;
import java.util.Random;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        CircuitBreakerConfig config = new CircuitBreakerConfig(
                0.5f,
                5,
                SlidingWindowType.COUNT_BASED,
                Duration.ofSeconds(3),
                2
        );

        CircuitBreaker cb = new CircuitBreaker(config);

        Random random = new Random();

        for (int i = 1; i <= 30; i++) {
            System.out.println("\nCall #" + i + " | State: " + cb.getState());

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

            Thread.sleep(500);
        }
    }
}