package io.github.sandumotelica.circuitbreaker.benchmarks;

import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

final class BenchmarkPayload {
    static volatile int value = 42;
    static final Supplier<Integer> SUCCESS = () -> value;
    static final Supplier<Integer> SLOW = () -> {
        LockSupport.parkNanos(2_000_000);
        return value;
    };
    static final IllegalStateException FAILURE_EXCEPTION = new IllegalStateException("simulated failure");
    static final Supplier<Integer> FAILURE = () -> {
        throw FAILURE_EXCEPTION;
    };

    private BenchmarkPayload() {
    }
}
