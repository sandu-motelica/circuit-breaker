package io.github.sandumotelica.circuitbreaker.scenario;

import java.util.ArrayList;
import java.util.List;

record ComparisonSummary(String scenario, int plannedRequests, String customFinalState,
                         String resilience4jFinalState, Counts overall, List<PhaseComparison> phases) {
    static ComparisonSummary from(RunSummary custom, RunSummary resilience4j) {
        if (!custom.scenario().equals(resilience4j.scenario())
                || custom.plannedRequests() != resilience4j.plannedRequests()
                || custom.phases().size() != resilience4j.phases().size()) {
            throw new IllegalArgumentException("Runs must use the same scenario plan");
        }
        List<PhaseComparison> phases = new ArrayList<>();
        for (int i = 0; i < custom.phases().size(); i++) {
            RunSummary.PhaseSummary left = custom.phases().get(i);
            RunSummary.PhaseSummary right = resilience4j.phases().get(i);
            if (!left.phase().equals(right.phase()) || left.requests() != right.requests()) {
                throw new IllegalArgumentException("Runs must have matching phases");
            }
            phases.add(new PhaseComparison(left.phase(), new PhaseCounts(
                    Metric.of(left.successful(), right.successful()),
                    Metric.of(left.upstreamFailures(), right.upstreamFailures()),
                    Metric.of(left.rejected(), right.rejected()),
                    Metric.of(left.other(), right.other()),
                    Latency.of(left.averageLatencyMs(), right.averageLatencyMs()),
                    Metric.of(left.p95LatencyMs(), right.p95LatencyMs()))));
        }
        return new ComparisonSummary(custom.scenario(), custom.plannedRequests(),
                custom.finalState(), resilience4j.finalState(), new Counts(
                Metric.of(custom.successful(), resilience4j.successful()),
                Metric.of(custom.upstreamFailures(), resilience4j.upstreamFailures()),
                Metric.of(custom.rejected(), resilience4j.rejected()),
                Metric.of(custom.unexpectedResponses() + custom.transportErrors(),
                        resilience4j.unexpectedResponses() + resilience4j.transportErrors()),
                Metric.of(custom.upstreamRequests(), resilience4j.upstreamRequests()),
                Latency.of(custom.averageLatencyMs(), resilience4j.averageLatencyMs()),
                Metric.of(custom.p95LatencyMs(), resilience4j.p95LatencyMs())), List.copyOf(phases));
    }

    record Counts(Metric successful, Metric upstreamFailures, Metric rejected, Metric other,
                  Metric upstreamRequests, Latency averageLatencyMs, Metric p95LatencyMs) {
    }

    record PhaseCounts(Metric successful, Metric upstreamFailures, Metric rejected, Metric other,
                       Latency averageLatencyMs, Metric p95LatencyMs) {
    }

    record PhaseComparison(String phase, PhaseCounts counts) {
    }

    record Metric(long custom, long resilience4j, long delta) {
        static Metric of(long custom, long resilience4j) {
            return new Metric(custom, resilience4j, resilience4j - custom);
        }
    }

    record Latency(double custom, double resilience4j, double delta) {
        static Latency of(double custom, double resilience4j) {
            return new Latency(custom, resilience4j, resilience4j - custom);
        }
    }
}
