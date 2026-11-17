package io.github.sandumotelica.circuitbreaker.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DirectCallBenchmark {
    @Benchmark
    public int directSupplier() {
        return BenchmarkPayload.SUCCESS.get();
    }
}
