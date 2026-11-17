package io.github.sandumotelica.circuitbreaker.demo.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedClientIntegrationTest {
    private static final AtomicInteger upstreamCalls = new AtomicInteger();
    private static final AtomicReference<String> lastUpstreamQuery = new AtomicReference<>();
    private static final HttpServer upstream = startUpstream();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreaker resilience4jCircuitBreaker;

    @BeforeEach
    void resetState() {
        circuitBreaker.reset();
        resilience4jCircuitBreaker.reset();
        upstreamCalls.set(0);
    }

    @DynamicPropertySource
    static void upstreamAddress(DynamicPropertyRegistry registry) {
        registry.add("demo.client.upstream-base-url",
                () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    void opensAfterFailuresAndRejectsWithoutContactingUpstream() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("payment-123"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/payments/payment-123").param("mode", "FAILURE"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("UPSTREAM_FAILURE"));
        }

        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/circuit-breaker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.failureRate").value(0.5))
                .andExpect(jsonPath("$.totalCalls").value(4))
                .andExpect(jsonPath("$.failedCalls").value(2))
                .andExpect(jsonPath("$.rejectedCalls").value(0));

        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"));

        mockMvc.perform(get("/api/circuit-breaker"))
                .andExpect(jsonPath("$.rejectedCalls").value(1));
        assertEquals(4, upstreamCalls.get());

        mockMvc.perform(post("/api/circuit-breaker/reset"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/circuit-breaker"))
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.failureRate").value(0))
                .andExpect(jsonPath("$.totalCalls").value(0))
                .andExpect(jsonPath("$.failedCalls").value(0))
                .andExpect(jsonPath("$.rejectedCalls").value(0));

        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isOk());
        assertEquals(5, upstreamCalls.get());
    }

    @Test
    void forwardsExplicitDelayAndRejectsInvalidDelayLocally() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123")
                        .param("mode", "SUCCESS")
                        .param("delayMs", "0"))
                .andExpect(status().isOk());
        assertTrue(lastUpstreamQuery.get().contains("mode=SUCCESS"));
        assertTrue(lastUpstreamQuery.get().contains("delayMs=0"));

        mockMvc.perform(get("/api/payments/payment-123")
                        .param("mode", "SUCCESS")
                        .param("delayMs", "-1"))
                .andExpect(status().isBadRequest());
        assertEquals(1, upstreamCalls.get());
        mockMvc.perform(get("/api/circuit-breaker"))
                .andExpect(jsonPath("$.totalCalls").value(1));
    }

    @Test
    void resilience4jRouteOpensIndependentlyAndCanBeReset() throws Exception {
        String route = "/api/payments/resilience4j/payment-123";
        mockMvc.perform(get(route).param("mode", "SUCCESS").param("delayMs", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("payment-123"));
        assertTrue(lastUpstreamQuery.get().contains("mode=SUCCESS"));
        assertTrue(lastUpstreamQuery.get().contains("delayMs=0"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(route).param("mode", "FAILURE"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("UPSTREAM_FAILURE"));
        }
        mockMvc.perform(get(route).param("mode", "SUCCESS"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resilience4j/circuit-breaker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.failureRate").value(50.0))
                .andExpect(jsonPath("$.bufferedCalls").value(4))
                .andExpect(jsonPath("$.failedCalls").value(2));

        mockMvc.perform(get(route).param("mode", "SUCCESS"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"));
        assertEquals(4, upstreamCalls.get());

        mockMvc.perform(get("/api/circuit-breaker"))
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.totalCalls").value(0));
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isOk());
        assertEquals(5, upstreamCalls.get());

        mockMvc.perform(post("/api/resilience4j/circuit-breaker/reset"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/resilience4j/circuit-breaker"))
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.bufferedCalls").value(0));
        mockMvc.perform(get(route).param("mode", "SUCCESS"))
                .andExpect(status().isOk());
        assertEquals(6, upstreamCalls.get());
    }

    @Test
    void resilience4jRouteRejectsInvalidDelayBeforeCallingUpstream() throws Exception {
        mockMvc.perform(get("/api/payments/resilience4j/payment-123")
                        .param("mode", "SUCCESS")
                        .param("delayMs", "-1"))
                .andExpect(status().isBadRequest());
        assertEquals(0, upstreamCalls.get());
        mockMvc.perform(get("/api/resilience4j/circuit-breaker"))
                .andExpect(jsonPath("$.bufferedCalls").value(0));
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/payments", exchange -> {
                upstreamCalls.incrementAndGet();
                lastUpstreamQuery.set(exchange.getRequestURI().getQuery());
                boolean failure = exchange.getRequestURI().getQuery().contains("mode=FAILURE");
                int statusCode = failure ? 503 : 200;
                byte[] body = (failure
                        ? "{\"error\":\"simulated failure\"}"
                        : "{\"paymentId\":\"payment-123\",\"status\":\"APPROVED\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start test payment service", exception);
        }
    }
}
