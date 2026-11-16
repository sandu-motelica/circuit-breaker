package io.github.sandumotelica.circuitbreaker.demo.unstable;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class PaymentSimulationService {
    private final UnstableServiceProperties properties;

    PaymentSimulationService(UnstableServiceProperties properties) {
        this.properties = properties;
    }

    PaymentResponse simulate(String paymentId, ResponseMode mode) {
        boolean slow = mode == ResponseMode.SLOW || mode == ResponseMode.RANDOM
                && ThreadLocalRandom.current().nextDouble() < properties.slowProbability();
        boolean failure = mode == ResponseMode.FAILURE || mode == ResponseMode.RANDOM
                && ThreadLocalRandom.current().nextDouble() < properties.failureProbability();

        if (slow) {
            try {
                Thread.sleep(properties.slowDelay());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Payment simulation interrupted", exception);
            }
        }
        if (failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Simulated payment service failure");
        }
        return new PaymentResponse(paymentId, "APPROVED");
    }
}
