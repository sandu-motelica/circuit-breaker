/**
 * Public API for configuring, executing, and managing circuit breakers.
 *
 * <p>A minimal circuit breaker can be created and used as follows:</p>
 *
 * <pre>{@code
 * CircuitBreakerConfig config = CircuitBreakerConfig.builder()
 *         .failureRateThreshold(0.5f)
 *         .slidingWindowSize(10)
 *         .minimumCalls(5)
 *         .build();
 *
 * CircuitBreaker circuitBreaker = new CircuitBreaker(config);
 * String result = circuitBreaker.execute(() -> remoteService.call());
 * }</pre>
 */
package io.github.sandumotelica.circuitbreaker;
