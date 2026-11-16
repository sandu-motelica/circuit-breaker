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
