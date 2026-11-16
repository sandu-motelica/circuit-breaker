package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class PaymentClient {
    private final CircuitBreaker circuitBreaker;
    private final RestClient restClient;

    PaymentClient(CircuitBreaker circuitBreaker, RestClient.Builder restClientBuilder,
                  ProtectedClientProperties properties) {
        this.circuitBreaker = circuitBreaker;
        this.restClient = restClientBuilder.baseUrl(properties.upstreamBaseUrl()).build();
    }

    PaymentResponse getPayment(String paymentId, ResponseMode mode, Long delayMs) {
        return circuitBreaker.execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/payments/{paymentId}").queryParam("mode", mode.name());
                    if (delayMs != null) {
                        uriBuilder.queryParam("delayMs", delayMs);
                    }
                    return uriBuilder.build(paymentId);
                })
                .retrieve()
                .body(PaymentResponse.class));
    }
}
