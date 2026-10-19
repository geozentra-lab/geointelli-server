package com.geointelli.ai.property.service.advice;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.exception.ImageStorageException;
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

    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<ApiError> handleImageStorage(ImageStorageException ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                .status(500)
                .error("Image Storage Error")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(ImageDownloadException.class)
    public ResponseEntity<ApiError> handleImageDownload(ImageDownloadException ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                .status(500)
                .error("Image Download Error")
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

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameterType(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return handleBadRequest(new IllegalArgumentException(ex.getName() + ": invalid value"));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return handleBadRequest(new IllegalArgumentException(ex.getParameterName() + " is required"));
    }

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<ApiError> handleInvalidParameters(org.springframework.validation.BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + (error.isBindingFailure()
                ? "invalid value" : error.getDefaultMessage()))
            .collect(java.util.stream.Collectors.joining("; "));
        return handleBadRequest(new IllegalArgumentException(message.isEmpty() ? "Invalid parameters" : message));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return handleBadRequest(new IllegalArgumentException("Invalid JSON body or unsupported field value"));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ApiError.builder()
            .status(ex.getStatusCode().value())
            .error(ex.getStatusCode().toString())
            .message(ex.getReason())
            .timestamp(Instant.now())
            .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // ex.printStackTrace();
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
