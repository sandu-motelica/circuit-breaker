package io.github.sandumotelica.circuitbreaker;

import io.github.sandumotelica.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.sandumotelica.circuitbreaker.ConcurrentTestSupport.runConcurrently;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerRegistryTest {

    @Test
    void returnSameInstanceForSameName() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder().build();

        CircuitBreaker first = registry.circuitBreaker("payment-service");
        CircuitBreaker second = registry.circuitBreaker("payment-service");
        CircuitBreaker other = registry.circuitBreaker("email-service");

        assertSame(first, second);
        assertNotSame(first, other);
        assertSame(first, registry.find("payment-service").orElseThrow());
        assertEquals(Map.of(
                "payment-service", first,
                "email-service", other
        ), registry.getAllCircuitBreakers());
    }

    @Test
    void useSpecificConfigAndFallBackToDefaultConfig() {
        CircuitBreakerConfig commonConfig = configWithFailureRate(1.0f);
        CircuitBreakerConfig paymentConfig = commonConfig.toBuilder()
                .failureRateThreshold(0.5f)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder()
                .defaultConfig(commonConfig)
                .serviceConfig("payment-service", paymentConfig)
                .build();

        CircuitBreaker payment = registry.circuitBreaker("payment-service");
        CircuitBreaker email = registry.circuitBreaker("email-service");

        recordOneFailureAndOneSuccess(payment);
        recordOneFailureAndOneSuccess(email);

        assertEquals(CircuitBreakerState.OPEN, payment.getState());
        assertEquals(CircuitBreakerState.CLOSED, email.getState());
    }

    @Test
    void createOnlyOneInstanceUnderConcurrentAccess() throws Exception {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder().build();
        Set<CircuitBreaker> instances = ConcurrentHashMap.newKeySet();

        runConcurrently(100,
                () -> instances.add(registry.circuitBreaker("payment-service")));

        assertEquals(1, instances.size());
    }

    @Test
    void removeExistingCircuitBreakerAndRecreateIt() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder().build();
        CircuitBreaker first = registry.circuitBreaker("payment-service");

        assertTrue(registry.remove("payment-service"));
        assertFalse(registry.find("payment-service").isPresent());

        CircuitBreaker recreated = registry.circuitBreaker("payment-service");
        assertNotSame(first, recreated);
        assertFalse(registry.remove("missing-service"));
    }

    @Test
    void rejectInvalidNamesAndConfigs() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.builder().build();

        assertThrows(IllegalArgumentException.class, () -> registry.circuitBreaker(" "));
        assertThrows(IllegalArgumentException.class,
                () -> CircuitBreakerRegistry.builder().defaultConfig(null));
        assertThrows(IllegalArgumentException.class,
                () -> CircuitBreakerRegistry.builder().serviceConfig("payment-service", null));
    }

    private static CircuitBreakerConfig configWithFailureRate(float threshold) {
        return CircuitBreakerConfig.builder()
                .failureRateThreshold(threshold)
                .slidingWindowSize(2)
                .minimumCalls(2)
                .build();
    }

    private static void recordOneFailureAndOneSuccess(CircuitBreaker circuitBreaker) {
        assertThrows(RuntimeException.class,
                () -> circuitBreaker.execute(() -> {
                    throw new RuntimeException("Failure");
                }));
        circuitBreaker.execute(() -> "Success");
    }
}
