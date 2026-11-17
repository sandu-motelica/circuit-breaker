package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.SlidingWindowType;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.client")
record ProtectedClientProperties(
        String upstreamBaseUrl,
        SlidingWindowType slidingWindowType,
        int slidingWindowSize,
        int minimumCalls,
        float failureRateThreshold,
        float slowCallRateThreshold,
        Duration slowCallDurationThreshold,
        Duration durationInOpenState,
        int permittedCallsInHalfOpen) {
}
