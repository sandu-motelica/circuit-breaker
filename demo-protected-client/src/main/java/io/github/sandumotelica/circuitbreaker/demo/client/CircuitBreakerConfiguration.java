package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import io.github.sandumotelica.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CircuitBreakerConfiguration {
    @Bean
    CircuitBreaker paymentCircuitBreaker(ProtectedClientProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .slidingWindowType(properties.slidingWindowType())
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumCalls(properties.minimumCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .slowCallRateThreshold(properties.slowCallRateThreshold())
                .slowCallDurationThreshold(properties.slowCallDurationThreshold())
                .durationInOpenState(properties.durationInOpenState())
                .permittedCallsInHalfOpen(properties.permittedCallsInHalfOpen())
                .build();
        return new CircuitBreaker(config);
    }
}
