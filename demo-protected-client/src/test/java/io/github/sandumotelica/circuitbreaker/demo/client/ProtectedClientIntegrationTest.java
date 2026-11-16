package io.github.sandumotelica.circuitbreaker.demo.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedClientIntegrationTest {
    private static final AtomicInteger upstreamCalls = new AtomicInteger();
    private static final HttpServer upstream = startUpstream();

    @Autowired
    private MockMvc mockMvc;

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
        org.junit.jupiter.api.Assertions.assertEquals(4, upstreamCalls.get());
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/payments", exchange -> {
                upstreamCalls.incrementAndGet();
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
