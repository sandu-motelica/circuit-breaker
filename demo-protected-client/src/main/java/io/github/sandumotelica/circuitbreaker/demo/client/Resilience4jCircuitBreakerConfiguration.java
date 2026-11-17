package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.sandumotelica.circuitbreaker.SlidingWindowType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class Resilience4jCircuitBreakerConfiguration {
    @Bean
    CircuitBreaker resilience4jPaymentCircuitBreaker(ProtectedClientProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(properties.slidingWindowType() == SlidingWindowType.TIME_BASED
                        ? CircuitBreakerConfig.SlidingWindowType.TIME_BASED
                        : CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumCalls())
                .failureRateThreshold(properties.failureRateThreshold() * 100)
                .slowCallRateThreshold(properties.slowCallRateThreshold() * 100)
                .slowCallDurationThreshold(properties.slowCallDurationThreshold())
                .waitDurationInOpenState(properties.durationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpen())
                .build();
        return CircuitBreaker.of("payment", config);
    }
}
