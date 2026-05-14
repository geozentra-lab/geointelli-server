package com.geointelli.ai.property.service.advice;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError.builder()
                .status(404)
                .error("Not Found")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiError.builder()
                .status(400)
                .error("Bad Request")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        ex.printStackTrace(); // ← add this temporarily
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                .status(500)
                .error("Internal Server Error")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }
}