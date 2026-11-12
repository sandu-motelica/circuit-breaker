package io.github.sandumotelica.circuitbreaker;

public class CallNotPermittedException extends RuntimeException {
    public CallNotPermittedException(String msg) {
        super(msg);
    }
}
