package com.sm.cb.registry;

import com.sm.cb.CircuitBreaker;
import com.sm.cb.CircuitBreakerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CircuitBreakerRegistry {
    private final CircuitBreakerConfig defaultConfig;
    private final Map<String, CircuitBreakerConfig> serviceConfigs;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers;

    private CircuitBreakerRegistry(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.serviceConfigs = Map.copyOf(builder.serviceConfigs);
        this.circuitBreakers = new ConcurrentHashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CircuitBreakerRegistry of(CircuitBreakerConfig defaultConfig) {
        return builder().defaultConfig(defaultConfig).build();
    }

    public CircuitBreaker circuitBreaker(String name) {
        String validName = validateName(name);
        return circuitBreakers.computeIfAbsent(validName, this::createCircuitBreaker);
    }

    public Optional<CircuitBreaker> find(String name) {
        return Optional.ofNullable(circuitBreakers.get(validateName(name)));
    }

    public Map<String, CircuitBreaker> getAllCircuitBreakers() {
        return Map.copyOf(circuitBreakers);
    }

    public boolean remove(String name) {
        return circuitBreakers.remove(validateName(name)) != null;
    }

    private CircuitBreaker createCircuitBreaker(String name) {
        CircuitBreakerConfig config = serviceConfigs.getOrDefault(name, defaultConfig);
        return new CircuitBreaker(config);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Circuit breaker name must not be blank");
        }
        return name;
    }

    public static final class Builder {
        private CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.builder().build();
        private final Map<String, CircuitBreakerConfig> serviceConfigs = new HashMap<>();

        private Builder() {
        }

        public Builder defaultConfig(CircuitBreakerConfig defaultConfig) {
            if (defaultConfig == null) {
                throw new IllegalArgumentException("Default config must not be null");
            }
            this.defaultConfig = defaultConfig;
            return this;
        }

        public Builder serviceConfig(String serviceName, CircuitBreakerConfig config) {
            if (config == null) {
                throw new IllegalArgumentException("Service config must not be null");
            }
            serviceConfigs.put(validateName(serviceName), config);
            return this;
        }

        public CircuitBreakerRegistry build() {
            return new CircuitBreakerRegistry(this);
        }
    }
}
