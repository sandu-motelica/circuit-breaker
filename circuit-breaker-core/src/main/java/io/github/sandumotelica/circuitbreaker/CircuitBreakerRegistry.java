package io.github.sandumotelica.circuitbreaker;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry that creates and stores circuit breakers by name.
 *
 * <p>Each name resolves to one shared {@link CircuitBreaker} instance. A
 * service-specific configuration is used when present; otherwise the registry's
 * default configuration is used.</p>
 */
public final class CircuitBreakerRegistry {
    private final CircuitBreakerConfig defaultConfig;
    private final Map<String, CircuitBreakerConfig> serviceConfigs;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers;

    private CircuitBreakerRegistry(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.serviceConfigs = Map.copyOf(builder.serviceConfigs);
        this.circuitBreakers = new ConcurrentHashMap<>();
    }

    /**
     * Creates a registry builder initialized with the default circuit-breaker configuration.
     *
     * @return a new registry builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a registry with the supplied default configuration.
     *
     * @param defaultConfig configuration used for services without an override
     * @return a new registry
     * @throws IllegalArgumentException if {@code defaultConfig} is {@code null}
     */
    public static CircuitBreakerRegistry of(CircuitBreakerConfig defaultConfig) {
        return builder().defaultConfig(defaultConfig).build();
    }

    /**
     * Returns the breaker registered under the given name, creating it atomically
     * if it does not yet exist.
     *
     * @param name non-blank circuit-breaker name
     * @return the existing or newly created breaker
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
    public CircuitBreaker circuitBreaker(String name) {
        String validName = validateName(name);
        return circuitBreakers.computeIfAbsent(validName, this::createCircuitBreaker);
    }

    /**
     * Finds an already-created circuit breaker without creating a new one.
     *
     * @param name non-blank circuit-breaker name
     * @return the registered breaker, or an empty optional if none exists
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
    public Optional<CircuitBreaker> find(String name) {
        return Optional.ofNullable(circuitBreakers.get(validateName(name)));
    }

    /**
     * Returns an immutable snapshot of all currently registered circuit breakers.
     *
     * @return mapping from names to breaker instances
     */
    public Map<String, CircuitBreaker> getAllCircuitBreakers() {
        return Map.copyOf(circuitBreakers);
    }

    /**
     * Removes the circuit breaker registered under the given name.
     * A later lookup may create a new instance for the same name.
     *
     * @param name non-blank circuit-breaker name
     * @return {@code true} if an instance was removed
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
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

    /**
     * Builds registries with a shared default configuration and optional
     * service-specific overrides.
     */
    public static final class Builder {
        private CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.builder().build();
        private final Map<String, CircuitBreakerConfig> serviceConfigs = new HashMap<>();

        private Builder() {
        }

        /**
         * Sets the configuration used when no service-specific override exists.
         *
         * @param defaultConfig default circuit-breaker configuration
         * @return this builder
         * @throws IllegalArgumentException if {@code defaultConfig} is {@code null}
         */
        public Builder defaultConfig(CircuitBreakerConfig defaultConfig) {
            if (defaultConfig == null) {
                throw new IllegalArgumentException("Default config must not be null");
            }
            this.defaultConfig = defaultConfig;
            return this;
        }

        /**
         * Associates a configuration with one service name.
         *
         * @param serviceName non-blank service name
         * @param config configuration used for that service
         * @return this builder
         * @throws IllegalArgumentException if the name is blank or the configuration is {@code null}
         */
        public Builder serviceConfig(String serviceName, CircuitBreakerConfig config) {
            if (config == null) {
                throw new IllegalArgumentException("Service config must not be null");
            }
            serviceConfigs.put(validateName(serviceName), config);
            return this;
        }

        /**
         * Creates a registry from this builder's configuration.
         *
         * @return a new circuit-breaker registry
         */
        public CircuitBreakerRegistry build() {
            return new CircuitBreakerRegistry(this);
        }
    }
}
