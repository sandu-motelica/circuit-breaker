package io.github.sandumotelica.circuitbreaker.benchmarks;

import java.util.function.Supplier;

final class BenchmarkPayload {
    static volatile int value = 42;
    static final Supplier<Integer> SUCCESS = () -> value;
    static final IllegalStateException FAILURE_EXCEPTION = new IllegalStateException("simulated failure");
    static final Supplier<Integer> FAILURE = () -> {
        throw FAILURE_EXCEPTION;
    };

    private BenchmarkPayload() {
    }
}
