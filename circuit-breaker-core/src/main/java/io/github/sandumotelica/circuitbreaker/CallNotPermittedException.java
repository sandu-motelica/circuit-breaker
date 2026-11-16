package io.github.sandumotelica.circuitbreaker;

/**
 * Indicates that a circuit breaker rejected a call before invoking the operation.
 */
public class CallNotPermittedException extends RuntimeException {
    /**
     * Creates an exception describing why the call was rejected.
     *
     * @param msg rejection reason
     */
    public CallNotPermittedException(String msg) {
        super(msg);
    }
}
