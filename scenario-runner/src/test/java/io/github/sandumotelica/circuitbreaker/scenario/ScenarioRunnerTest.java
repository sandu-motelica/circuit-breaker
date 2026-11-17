package io.github.sandumotelica.circuitbreaker.scenario;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resetsServicesAndWritesMeasuredResults() throws Exception {
        Path scenario = temporaryDirectory.resolve("scenario.csv");
        Files.writeString(scenario, "phase,count,intervalMs,modes,delaysMs,pauseBeforeMs\n"
                + "mixed,3,0,SUCCESS|FAILURE|SUCCESS,20,0\n");
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicInteger upstreamResets = new AtomicInteger();
        AtomicInteger breakerResets = new AtomicInteger();
        AtomicInteger forwardedDelays = new AtomicInteger();
        HttpServer upstream = server();
        HttpServer client = server();
        upstream.createContext("/api/simulation/reset", exchange -> {
            upstreamCalls.set(0);
            upstreamResets.incrementAndGet();
            respond(exchange, 204, "");
        });
        upstream.createContext("/api/simulation/stats", exchange -> respond(exchange, 200,
                "{\"receivedRequests\":" + upstreamCalls.get() + "}"));
        client.createContext("/api/circuit-breaker/reset", exchange -> {
            breakerResets.incrementAndGet();
            respond(exchange, 204, "");
        });
        client.createContext("/api/circuit-breaker", exchange -> respond(exchange, 200,
                "{\"state\":\"OPEN\",\"totalCalls\":2,\"failedCalls\":1,\"rejectedCalls\":1}"));
        client.createContext("/api/payments", exchange -> {
            if (exchange.getRequestURI().getQuery().contains("delayMs=20")) {
                forwardedDelays.incrementAndGet();
            }
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("scenario-3")) {
                respond(exchange, 503, "{\"code\":\"CIRCUIT_OPEN\"}");
            } else {
                upstreamCalls.incrementAndGet();
                if (path.endsWith("scenario-2")) {
                    respond(exchange, 502, "{\"code\":\"UPSTREAM_FAILURE\"}");
                } else {
                    respond(exchange, 200, "{\"status\":\"APPROVED\"}");
                }
            }
        });
        upstream.start();
        client.start();
        try {
            Path output = temporaryDirectory.resolve("results");
            RunSummary summary = new ScenarioRunner().run(ScenarioPlan.load(scenario), output,
                    1, address(client), address(upstream));

            assertEquals(3, summary.plannedRequests());
            assertEquals("scenario.csv", summary.scenario());
            assertEquals(1, summary.phases().size());
            assertEquals(1, summary.phases().getFirst().rejected());
            assertEquals(1, summary.successful());
            assertEquals(1, summary.upstreamFailures());
            assertEquals(1, summary.rejected());
            assertEquals(2, summary.upstreamRequests());
            assertEquals(1, upstreamResets.get());
            assertEquals(1, breakerResets.get());
            assertEquals(3, forwardedDelays.get());
            assertEquals(4, Files.readAllLines(output.resolve("calls.csv")).size());
            assertTrue(Files.readString(output.resolve("summary.json")).contains("\"upstreamRequests\" : 2"));
        } finally {
            client.stop(0);
            upstream.stop(0);
        }
    }

    @Test
    void rejectsRandomModeBeforeSendingRequests() throws IOException {
        Path scenario = temporaryDirectory.resolve("invalid.csv");
        Files.writeString(scenario, "phase,count,intervalMs,modes,delaysMs,pauseBeforeMs\n"
                + "unstable,2,10,RANDOM,20,0\n");
        assertThrows(IllegalArgumentException.class, () -> ScenarioPlan.load(scenario));
    }

    @Test
    void runsIdenticalPlanThroughBothRoutesAndComparesOutcomes() throws Exception {
        Path scenario = temporaryDirectory.resolve("both.csv");
        Files.writeString(scenario, "phase,count,intervalMs,modes,delaysMs,pauseBeforeMs\n"
                + "healthy,2,20,SUCCESS,0,0\n");
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicInteger customCalls = new AtomicInteger();
        AtomicInteger resilience4jCalls = new AtomicInteger();
        AtomicInteger customResets = new AtomicInteger();
        AtomicInteger resilience4jResets = new AtomicInteger();
        HttpServer upstream = server();
        HttpServer client = server();
        upstream.createContext("/api/simulation/reset", exchange -> {
            upstreamCalls.set(0);
            respond(exchange, 204, "");
        });
        upstream.createContext("/api/simulation/stats", exchange -> respond(exchange, 200,
                "{\"receivedRequests\":" + upstreamCalls.get() + "}"));
        client.createContext("/api/circuit-breaker/reset", exchange -> {
            customResets.incrementAndGet();
            respond(exchange, 204, "");
        });
        client.createContext("/api/circuit-breaker", exchange -> respond(exchange, 200,
                "{\"state\":\"CLOSED\",\"totalCalls\":2}"));
        client.createContext("/api/resilience4j/circuit-breaker/reset", exchange -> {
            resilience4jResets.incrementAndGet();
            respond(exchange, 204, "");
        });
        client.createContext("/api/resilience4j/circuit-breaker", exchange -> respond(exchange, 200,
                "{\"state\":\"CLOSED\",\"bufferedCalls\":2}"));
        client.createContext("/api/payments/resilience4j/", exchange -> {
            resilience4jCalls.incrementAndGet();
            upstreamCalls.incrementAndGet();
            respond(exchange, 200, "{\"status\":\"APPROVED\"}");
        });
        client.createContext("/api/payments/", exchange -> {
            customCalls.incrementAndGet();
            upstreamCalls.incrementAndGet();
            respond(exchange, 200, "{\"status\":\"APPROVED\"}");
        });
        upstream.start();
        client.start();
        try {
            ScenarioRunner runner = new ScenarioRunner();
            ScenarioPlan plan = ScenarioPlan.load(scenario);
            RunSummary custom = runner.run(plan, temporaryDirectory.resolve("custom"),
                    1, address(client), address(upstream), Target.CUSTOM);
            RunSummary resilience4j = runner.run(plan, temporaryDirectory.resolve("resilience4j"),
                    1, address(client), address(upstream), Target.RESILIENCE4J);
            ComparisonSummary comparison = ComparisonSummary.from(custom, resilience4j);

            assertEquals(2, customCalls.get());
            assertEquals(2, resilience4jCalls.get());
            assertEquals(1, customResets.get());
            assertEquals(1, resilience4jResets.get());
            assertEquals(2, custom.upstreamRequests());
            assertEquals(2, resilience4j.upstreamRequests());
            assertEquals(0, comparison.overall().successful().delta());
            assertEquals(0, comparison.overall().upstreamRequests().delta());
            assertEquals("CLOSED", comparison.resilience4jFinalState());
            String comparisonJson = new tools.jackson.databind.ObjectMapper().writeValueAsString(comparison);
            assertTrue(comparisonJson.contains("\"phase\":\"healthy\""));
            assertFalse(comparisonJson.contains("\"upstreamRequests\":null"));
            assertTrue(Files.exists(temporaryDirectory.resolve("resilience4j/summary.json")));
        } finally {
            client.stop(0);
            upstream.stop(0);
        }
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static URI address(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
