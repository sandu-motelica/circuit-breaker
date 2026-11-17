package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
class PaymentClient {
    private final CircuitBreaker circuitBreaker;
    private final UpstreamPaymentClient upstreamPaymentClient;

    PaymentClient(CircuitBreaker circuitBreaker, UpstreamPaymentClient upstreamPaymentClient) {
        this.circuitBreaker = circuitBreaker;
        this.upstreamPaymentClient = upstreamPaymentClient;
    }

    PaymentResponse getPayment(String paymentId, ResponseMode mode, Long delayMs) {
        return circuitBreaker.execute(() -> upstreamPaymentClient.getPayment(paymentId, mode, delayMs));
    }
}
