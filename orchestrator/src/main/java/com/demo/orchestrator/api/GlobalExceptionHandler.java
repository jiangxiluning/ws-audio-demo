package com.demo.orchestrator.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.demo.orchestrator.api.dto.ErrorResponse;
import com.demo.orchestrator.exception.InvalidDurationException;
import com.demo.orchestrator.exception.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDurationException.class)
    public ResponseEntity<ErrorResponse> invalidDuration(InvalidDurationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_DURATION", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }
}
