package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resilience4j/circuit-breaker")
class Resilience4jCircuitBreakerStatusController {
    private final CircuitBreaker circuitBreaker;

    Resilience4jCircuitBreakerStatusController(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping
    Resilience4jCircuitBreakerStatus status() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return new Resilience4jCircuitBreakerStatus(
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfNotPermittedCalls());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() {
        circuitBreaker.reset();
    }
}
