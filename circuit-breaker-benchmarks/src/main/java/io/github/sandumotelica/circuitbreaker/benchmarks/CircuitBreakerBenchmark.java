package io.github.sandumotelica.circuitbreaker.benchmarks;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.github.sandumotelica.circuitbreaker.CircuitBreaker;
import io.github.sandumotelica.circuitbreaker.CircuitBreakerConfig;
import io.github.sandumotelica.circuitbreaker.SlidingWindowType;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CircuitBreakerBenchmark {
    @Param({"custom", "resilience4j"})
    public String implementation;

    @Param({"count", "time"})
    public String window;

    private CircuitBreaker customSuccess;
    private CircuitBreaker customFailure;
    private CircuitBreaker customOpen;
    private io.github.resilience4j.circuitbreaker.CircuitBreaker resilience4jSuccess;
    private io.github.resilience4j.circuitbreaker.CircuitBreaker resilience4jFailure;
    private io.github.resilience4j.circuitbreaker.CircuitBreaker resilience4jOpen;

    @Setup(Level.Trial)
    public void setup() {
        boolean countBased = "count".equals(window);
        if ("custom".equals(implementation)) {
            CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                    .slidingWindowType(countBased ? SlidingWindowType.COUNT_BASED : SlidingWindowType.TIME_BASED)
                    .slidingWindowSize(countBased ? 1024 : 10)
                    .minimumCalls(1024)
                    .failureRateThreshold(1.0f)
                    .slowCallRateThreshold(1.0f)
                    .slowCallDurationThreshold(Duration.ofDays(1))
                    .durationInOpenState(Duration.ofDays(1))
                    .build();
            customSuccess = new CircuitBreaker(config);
            for (int i = 0; i < 1024; i++) {
                customSuccess.execute(BenchmarkPayload.SUCCESS);
            }
            customFailure = new CircuitBreaker(config);
            customFailure.transitionToMetricsOnlyState();
            customOpen = new CircuitBreaker(config);
            customOpen.transitionToOpenState();
        } else if ("resilience4j".equals(implementation)) {
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowType(countBased
                                    ? io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                                    : io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                            .slidingWindowSize(countBased ? 1024 : 10)
                            .minimumNumberOfCalls(1024)
                            .failureRateThreshold(100.0f)
                            .slowCallRateThreshold(100.0f)
                            .slowCallDurationThreshold(Duration.ofDays(1))
                            .waitDurationInOpenState(Duration.ofDays(1))
                            .build();
            resilience4jSuccess = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("success", config);
            for (int i = 0; i < 1024; i++) {
                resilience4jSuccess.executeSupplier(BenchmarkPayload.SUCCESS);
            }
            resilience4jFailure = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("failure", config);
            resilience4jFailure.transitionToMetricsOnlyState();
            resilience4jOpen = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("open", config);
            resilience4jOpen.transitionToOpenState();
        } else {
            throw new IllegalArgumentException("Unknown implementation: " + implementation);
        }
    }

    @Benchmark
    public int successfulCall() {
        return customSuccess != null
                ? customSuccess.execute(BenchmarkPayload.SUCCESS)
                : resilience4jSuccess.executeSupplier(BenchmarkPayload.SUCCESS);
    }

    @Benchmark
    @Threads(4)
    public int concurrentSuccessfulCall() {
        return successfulCall();
    }

    @Benchmark
    public RuntimeException failedCall() {
        try {
            if (customFailure != null) {
                customFailure.execute(BenchmarkPayload.FAILURE);
            } else {
                resilience4jFailure.executeSupplier(BenchmarkPayload.FAILURE);
            }
            throw new AssertionError("Expected failure");
        } catch (IllegalStateException exception) {
            return exception;
        }
    }

    @Benchmark
    public RuntimeException rejectedCall() {
        try {
            if (customOpen != null) {
                customOpen.execute(BenchmarkPayload.SUCCESS);
            } else {
                resilience4jOpen.executeSupplier(BenchmarkPayload.SUCCESS);
            }
            throw new AssertionError("Expected rejection");
        } catch (io.github.sandumotelica.circuitbreaker.CallNotPermittedException
                 | io.github.resilience4j.circuitbreaker.CallNotPermittedException exception) {
            return exception;
        }
    }

    @Benchmark
    public float readFailureRate() {
        return customSuccess != null
                ? customSuccess.getFailureRate()
                : resilience4jSuccess.getMetrics().getFailureRate();
    }
}
