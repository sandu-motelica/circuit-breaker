package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
class Resilience4jPaymentClient {
    private final CircuitBreaker circuitBreaker;
    private final UpstreamPaymentClient upstreamPaymentClient;

    Resilience4jPaymentClient(CircuitBreaker circuitBreaker, UpstreamPaymentClient upstreamPaymentClient) {
        this.circuitBreaker = circuitBreaker;
        this.upstreamPaymentClient = upstreamPaymentClient;
    }

    PaymentResponse getPayment(String paymentId, ResponseMode mode, Long delayMs) {
        return circuitBreaker.executeSupplier(() -> upstreamPaymentClient.getPayment(paymentId, mode, delayMs));
    }
}
