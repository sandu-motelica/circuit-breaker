package io.github.sandumotelica.circuitbreaker;

/**
 * Strategies for retaining recent call outcomes.
 */
public enum SlidingWindowType {
    /** Retains a fixed number of the most recent calls. */
    COUNT_BASED,
    /** Retains calls recorded during a fixed number of recent seconds. */
    TIME_BASED
}
