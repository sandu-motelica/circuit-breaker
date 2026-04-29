package com.sm.cb.exception;

public class CallNotPermittedException extends RuntimeException {
    public CallNotPermittedException(String msg) {
        super(msg);
    }
}
