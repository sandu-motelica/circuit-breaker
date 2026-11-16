package io.github.sandumotelica.circuitbreaker.demo.client;

import io.github.sandumotelica.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ApiError> circuitOpen() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("CIRCUIT_OPEN", "The payment service is temporarily unavailable"));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> upstreamFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("UPSTREAM_FAILURE", "The payment service request failed"));
    }
}
