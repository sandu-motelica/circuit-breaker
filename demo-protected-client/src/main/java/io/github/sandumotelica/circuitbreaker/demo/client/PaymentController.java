package io.github.sandumotelica.circuitbreaker.demo.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
class PaymentController {
    private final PaymentClient paymentClient;
    private final Resilience4jPaymentClient resilience4jPaymentClient;

    PaymentController(PaymentClient paymentClient, Resilience4jPaymentClient resilience4jPaymentClient) {
        this.paymentClient = paymentClient;
        this.resilience4jPaymentClient = resilience4jPaymentClient;
    }

    @GetMapping("/{paymentId}")
    PaymentResponse getPayment(
            @PathVariable("paymentId") String paymentId,
            @RequestParam(name = "mode", defaultValue = "RANDOM") ResponseMode mode,
            @RequestParam(name = "delayMs", required = false) Long delayMs) {
        validateDelay(delayMs);
        return paymentClient.getPayment(paymentId, mode, delayMs);
    }

    @GetMapping("/resilience4j/{paymentId}")
    PaymentResponse getPaymentWithResilience4j(
            @PathVariable("paymentId") String paymentId,
            @RequestParam(name = "mode", defaultValue = "RANDOM") ResponseMode mode,
            @RequestParam(name = "delayMs", required = false) Long delayMs) {
        validateDelay(delayMs);
        return resilience4jPaymentClient.getPayment(paymentId, mode, delayMs);
    }

    private static void validateDelay(Long delayMs) {
        if (delayMs != null && delayMs < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delayMs must be non-negative");
        }
    }
}
