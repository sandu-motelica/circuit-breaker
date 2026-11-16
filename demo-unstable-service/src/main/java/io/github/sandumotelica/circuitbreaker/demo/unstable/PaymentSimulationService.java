package io.github.sandumotelica.circuitbreaker.demo.unstable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class PaymentSimulationService {
    private final UnstableServiceProperties properties;
    private final AtomicLong receivedRequests = new AtomicLong();

    PaymentSimulationService(UnstableServiceProperties properties) {
        this.properties = properties;
    }

    PaymentResponse simulate(String paymentId, ResponseMode mode, Long delayMs) {
        if (delayMs != null && delayMs < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delayMs must be non-negative");
        }
        receivedRequests.incrementAndGet();

        boolean slow = mode == ResponseMode.SLOW || mode == ResponseMode.RANDOM
                && ThreadLocalRandom.current().nextDouble() < properties.slowProbability();
        boolean failure = mode == ResponseMode.FAILURE || mode == ResponseMode.RANDOM
                && ThreadLocalRandom.current().nextDouble() < properties.failureProbability();

        Duration delay = delayMs == null
                ? (slow ? properties.slowDelay() : Duration.ZERO)
                : Duration.ofMillis(delayMs);
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
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

    long receivedRequests() {
        return receivedRequests.get();
    }

    void resetStatistics() {
        receivedRequests.set(0);
    }
}
