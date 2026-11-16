package io.github.sandumotelica.circuitbreaker.demo.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentController {
    private final PaymentClient paymentClient;

    PaymentController(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @GetMapping("/{paymentId}")
    PaymentResponse getPayment(
            @PathVariable("paymentId") String paymentId,
            @RequestParam(name = "mode", defaultValue = "RANDOM") ResponseMode mode) {
        return paymentClient.getPayment(paymentId, mode);
    }
}
