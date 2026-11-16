package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import io.github.sandumotelica.circuitbreaker.observability.CircuitBreakerStatistics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/circuit-breaker")
class CircuitBreakerStatusController {
    private final CircuitBreaker circuitBreaker;

    CircuitBreakerStatusController(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping
    CircuitBreakerStatus status() {
        CircuitBreakerStatistics statistics = circuitBreaker.getStatistics();
        return new CircuitBreakerStatus(
                circuitBreaker.getState(),
                circuitBreaker.getFailureRate(),
                circuitBreaker.getSlowCallRate(),
                statistics.totalCalls(),
                statistics.failedCalls(),
                statistics.rejectedCalls(),
                statistics.averageDuration().toMillis());
    }
}
