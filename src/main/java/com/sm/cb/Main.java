package com.sm.cb;

import com.sm.cb.exception.CallNotPermittedException;
import com.sm.cb.observability.CircuitBreakerStatistics;
import com.sm.cb.registry.CircuitBreakerRegistry;
import com.sm.cb.window.SlidingWindowType;

import java.time.Duration;
import java.util.Random;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        CircuitBreakerConfig commonConfig = CircuitBreakerConfig.builder()
                .failureRateThreshold(0.7f)
                .slowCallRateThreshold(0.5f)
                .slowCallDurationThreshold(Duration.ofMillis(600))
                .slidingWindowSize(5)
                .minimumCalls(4)
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .durationInOpenState(Duration.ofSeconds(2))
                .permittedCallsInHalfOpen(2)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        CircuitBreakerConfig paymentServiceConfig = commonConfig.toBuilder()
                .failureRateThreshold(0.5f)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder()
                .defaultConfig(commonConfig)
                .serviceConfig("payment-service", paymentServiceConfig)
                .build();

        CircuitBreaker cb = registry.circuitBreaker("payment-service");
        cb.getEventPublisher().addListener(event -> System.out.println("[EVENT] " + event));

        Random random = new Random();

        for (int i = 1; i <= 30; i++) {
            System.out.println("\n--- Call #" + i + " ---");
            CircuitBreakerState stateBeforeCall = cb.getState();
            System.out.println("State before call: " + stateBeforeCall);

            try {
                String result = cb.execute(() -> {
                    int simulatedOutcome = random.nextInt(4);

                    if (simulatedOutcome == 0) {
                        throw new RuntimeException("Simulated failure");
                    }
                    if (simulatedOutcome == 1) {
                        throw new IllegalArgumentException("Simulated validation error (ignored)");
                    }
                    if (simulatedOutcome == 2) {
                        try {
                            Thread.sleep(700);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Call interrupted", e);
                        }
                        return "OK (slow)";
                    }
                    return "OK";
                });

                System.out.println("Result: " + result);
            } catch (CallNotPermittedException e) {
                System.out.println("Rejected call: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Ignored exception: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Recorded failure: " + e.getMessage());
            }

            CircuitBreakerState stateAfterCall = cb.getState();
            if (stateBeforeCall != stateAfterCall) {
                System.out.println("State transition: " + stateBeforeCall + " -> " + stateAfterCall);
            } else {
                System.out.println("State after call: " + stateAfterCall + " (unchanged)");
            }

            String metricsSource = stateAfterCall == CircuitBreakerState.CLOSED
                    ? "current CLOSED window"
                    : "last CLOSED window";
            System.out.printf(
                    "Metrics (%s): failure = %.0f%%, slow = %.0f%%%n",
                    metricsSource,
                    cb.getFailureRate() * 100,
                    cb.getSlowCallRate() * 100
            );

            CircuitBreakerStatistics statistics = cb.getStatistics();
            System.out.printf(
                    "Statistics: total = %d, failed = %d, rejected = %d, average = %d ms%n",
                    statistics.totalCalls(),
                    statistics.failedCalls(),
                    statistics.rejectedCalls(),
                    statistics.averageDuration().toMillis()
            );

            Thread.sleep(500);
        }
    }
}
