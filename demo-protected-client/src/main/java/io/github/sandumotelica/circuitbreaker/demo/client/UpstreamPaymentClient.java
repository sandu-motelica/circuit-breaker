package io.github.sandumotelica.circuitbreaker.demo.client;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class UpstreamPaymentClient {
    private final RestClient restClient;

    UpstreamPaymentClient(RestClient.Builder restClientBuilder, ProtectedClientProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.upstreamBaseUrl()).build();
    }

    PaymentResponse getPayment(String paymentId, ResponseMode mode, Long delayMs) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/payments/{paymentId}").queryParam("mode", mode.name());
                    if (delayMs != null) {
                        uriBuilder.queryParam("delayMs", delayMs);
                    }
                    return uriBuilder.build(paymentId);
                })
                .retrieve()
                .body(PaymentResponse.class);
    }
}
