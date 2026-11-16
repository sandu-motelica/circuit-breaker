package io.github.sandumotelica.circuitbreaker.demo.unstable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentController {
    private final PaymentSimulationService simulationService;

    PaymentController(PaymentSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/{paymentId}")
    PaymentResponse getPayment(
            @PathVariable("paymentId") String paymentId,
            @RequestParam(name = "mode", defaultValue = "RANDOM") ResponseMode mode,
            @RequestParam(name = "delayMs", required = false) Long delayMs) {
        return simulationService.simulate(paymentId, mode, delayMs);
    }
}
