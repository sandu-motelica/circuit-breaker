package io.github.sandumotelica.circuitbreaker.demo.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.client")
record ProtectedClientProperties(
        String upstreamBaseUrl,
        int slidingWindowSize,
        int minimumCalls,
        float failureRateThreshold,
        float slowCallRateThreshold,
        Duration slowCallDurationThreshold,
        Duration durationInOpenState,
        int permittedCallsInHalfOpen) {
}
