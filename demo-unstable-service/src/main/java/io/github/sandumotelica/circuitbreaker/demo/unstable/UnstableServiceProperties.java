package io.github.sandumotelica.circuitbreaker.demo.unstable;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.unstable")
record UnstableServiceProperties(double failureProbability, double slowProbability, Duration slowDelay) {
    UnstableServiceProperties {
        if (failureProbability < 0 || failureProbability > 1 || Double.isNaN(failureProbability)) {
            throw new IllegalArgumentException("Failure probability must be between 0 and 1");
        }
        if (slowProbability < 0 || slowProbability > 1 || Double.isNaN(slowProbability)) {
            throw new IllegalArgumentException("Slow probability must be between 0 and 1");
        }
        if (slowDelay == null || slowDelay.isNegative() || slowDelay.isZero()) {
            throw new IllegalArgumentException("Slow delay must be positive");
        }
    }
}
