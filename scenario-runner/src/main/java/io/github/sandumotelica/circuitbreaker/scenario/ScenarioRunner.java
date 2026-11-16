package io.github.sandumotelica.circuitbreaker.scenario;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runs a deterministic request plan against the protected client. */
public final class ScenarioRunner {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();

    ScenarioRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 5) {
            throw new IllegalArgumentException("Usage: java -jar scenario-runner-all.jar "
                    + "<scenario.csv> <output-dir> [threads] [client-url] [upstream-url]");
        }
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        URI client = URI.create(args.length > 3 ? args[3] : "http://localhost:8080");
        URI upstream = URI.create(args.length > 4 ? args[4] : "http://localhost:8081");
        ScenarioPlan plan = ScenarioPlan.load(Path.of(args[0]));
        RunSummary summary = new ScenarioRunner().run(plan, Path.of(args[1]), threads, client, upstream);
        System.out.printf("Completed %d requests: %d successful, %d upstream failures, "
                        + "%d rejected. Upstream received %d. Results: %s%n",
                summary.plannedRequests(), summary.successful(), summary.upstreamFailures(),
                summary.rejected(), summary.upstreamRequests(), Path.of(args[1]).toAbsolutePath());
    }

    RunSummary run(ScenarioPlan plan, Path outputDir, int threads, URI client, URI upstream)
            throws Exception {
        String clientBase = baseUrl(client);
        String upstreamBase = baseUrl(upstream);
        requireNoContent(upstreamBase + "/api/simulation/reset");
        requireNoContent(clientBase + "/api/circuit-breaker/reset");

        long startedAt = System.nanoTime();
        List<CallResult> results = new ArrayList<>(plan.calls().size());
        try (var scheduler = Executors.newScheduledThreadPool(threads)) {
            List<ScheduledFuture<CallResult>> futures = new ArrayList<>(plan.calls().size());
            for (ScenarioPlan.PlannedCall call : plan.calls()) {
                futures.add(scheduler.schedule(
                        () -> execute(call, clientBase, startedAt), call.scheduledMs(), TimeUnit.MILLISECONDS));
            }
            for (ScheduledFuture<CallResult> future : futures) {
                results.add(future.get());
            }
        }

        JsonNode upstreamStats = getJson(upstreamBase + "/api/simulation/stats");
        JsonNode breakerStats = getJson(clientBase + "/api/circuit-breaker");
        RunSummary summary = RunSummary.from(plan.name(), threads, clientBase, upstreamBase,
                results, upstreamStats.required("receivedRequests").longValue(), breakerStats);
        Files.createDirectories(outputDir);
        writeCalls(outputDir.resolve("calls.csv"), results);
        json.writerWithDefaultPrettyPrinter().writeValue(outputDir.resolve("summary.json").toFile(), summary);
        return summary;
    }

    private CallResult execute(ScenarioPlan.PlannedCall call, String clientBase, long startedAt) {
        long actualStart = System.nanoTime();
        long startMs = Duration.ofNanos(actualStart - startedAt).toMillis();
        String url = clientBase + "/api/payments/scenario-" + call.sequence()
                + "?mode=" + call.mode() + "&delayMs=" + call.delayMs();
        int status = 0;
        String result;
        String detail = "";
        try {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build());
            status = response.statusCode();
            result = switch (status) {
                case 200 -> "SUCCESS";
                case 502 -> "UPSTREAM_FAILURE";
                case 503 -> "REJECTED";
                default -> "UNEXPECTED_RESPONSE";
            };
            if (status == 502 || status == 503) {
                String expectedCode = status == 502 ? "UPSTREAM_FAILURE" : "CIRCUIT_OPEN";
                try {
                    JsonNode error = json.readTree(response.body());
                    if (!expectedCode.equals(error.path("code").asText())) {
                        result = "UNEXPECTED_RESPONSE";
                    }
                } catch (RuntimeException exception) {
                    result = "UNEXPECTED_RESPONSE";
                }
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            result = "TRANSPORT_ERROR";
            detail = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        long durationMs = Duration.ofNanos(System.nanoTime() - actualStart).toMillis();
        return new CallResult(call.sequence(), call.phase(), call.scheduledMs(), startMs,
                call.mode(), call.delayMs(), status, result, durationMs, detail);
    }

    private void requireNoContent(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody()).build());
        if (response.statusCode() != 204) {
            throw new IOException("Reset failed at " + url + ": HTTP " + response.statusCode());
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("Statistics failed at " + url + ": HTTP " + response.statusCode());
        }
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String baseUrl(URI uri) {
        String text = uri.toString();
        if (!(text.startsWith("http://") || text.startsWith("https://"))) {
            throw new IllegalArgumentException("Expected HTTP URL: " + uri);
        }
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private static void writeCalls(Path path, List<CallResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("sequence,phase,scheduledMs,startedMs,mode,delayMs,"
                + "httpStatus,result,durationMs,detail\n");
        for (CallResult call : results) {
            csv.append(call.sequence()).append(',').append(call.phase()).append(',')
                    .append(call.scheduledMs()).append(',').append(call.startedMs()).append(',')
                    .append(call.mode()).append(',').append(call.delayMs()).append(',')
                    .append(call.httpStatus()).append(',').append(call.result()).append(',')
                    .append(call.durationMs()).append(',').append(csvField(call.detail())).append('\n');
        }
        Files.writeString(path, csv);
    }

    private static String csvField(String value) {
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    record CallResult(int sequence, String phase, long scheduledMs, long startedMs,
                      String mode, long delayMs, int httpStatus, String result,
                      long durationMs, String detail) {
    }
}
