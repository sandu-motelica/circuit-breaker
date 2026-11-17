package io.github.sandumotelica.circuitbreaker.scenario;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;

record RunSummary(String scenario, String implementation, int threads, String clientUrl, String upstreamUrl,
                  int plannedRequests, long successful, long upstreamFailures, long rejected,
                  long unexpectedResponses, long transportErrors, long upstreamRequests,
                  String finalState, JsonNode breakerMetrics, double averageLatencyMs,
                  long p50LatencyMs, long p95LatencyMs, long p99LatencyMs,
                  List<PhaseSummary> phases) {
    static RunSummary from(String scenario, String implementation, int threads, String clientUrl, String upstreamUrl,
                           List<ScenarioRunner.CallResult> calls, long upstreamRequests, JsonNode breaker) {
        List<Long> durations = calls.stream().map(ScenarioRunner.CallResult::durationMs).sorted().toList();
        Map<String, List<ScenarioRunner.CallResult>> byPhase = calls.stream().collect(
                Collectors.groupingBy(ScenarioRunner.CallResult::phase, LinkedHashMap::new,
                        Collectors.toList()));
        List<PhaseSummary> phases = byPhase.entrySet().stream()
                .map(entry -> PhaseSummary.from(entry.getKey(), entry.getValue())).toList();
        return new RunSummary(scenario, implementation, threads, clientUrl, upstreamUrl,
                calls.size(), count(calls, "SUCCESS"),
                count(calls, "UPSTREAM_FAILURE"), count(calls, "REJECTED"),
                count(calls, "UNEXPECTED_RESPONSE"), count(calls, "TRANSPORT_ERROR"),
                upstreamRequests, breaker.required("state").asText(), breaker,
                calls.stream().mapToLong(ScenarioRunner.CallResult::durationMs).average().orElse(0),
                percentile(durations, 0.50), percentile(durations, 0.95), percentile(durations, 0.99),
                phases);
    }

    private static long count(List<ScenarioRunner.CallResult> calls, String result) {
        return calls.stream().filter(call -> result.equals(call.result())).count();
    }

    private static long percentile(List<Long> sorted, double percentile) {
        return sorted.get(Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
    }

    record PhaseSummary(String phase, int requests, long successful, long upstreamFailures,
                        long rejected, long other, double averageLatencyMs, long p95LatencyMs) {
        static PhaseSummary from(String phase, List<ScenarioRunner.CallResult> calls) {
            List<Long> durations = calls.stream().map(ScenarioRunner.CallResult::durationMs)
                    .sorted().toList();
            return new PhaseSummary(phase, calls.size(), count(calls, "SUCCESS"),
                    count(calls, "UPSTREAM_FAILURE"), count(calls, "REJECTED"),
                    count(calls, "UNEXPECTED_RESPONSE") + count(calls, "TRANSPORT_ERROR"),
                    calls.stream().mapToLong(ScenarioRunner.CallResult::durationMs).average().orElse(0),
                    percentile(durations, 0.95));
        }
    }
}
