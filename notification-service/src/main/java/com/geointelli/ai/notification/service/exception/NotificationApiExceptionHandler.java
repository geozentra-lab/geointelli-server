package com.geointelli.ai.notification.service.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Returns safe API errors without exposing persistence exceptions or request
 * payloads.
 */
@RestControllerAdvice
public class NotificationApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(),
                error.getReason(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getDefaultMessage()).findFirst().orElse("Invalid request");
        return badRequest(message);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ApiError> malformed(Exception error) {
        return badRequest("Invalid request body or parameter type");
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ApiError(400, "Bad Request", message, Instant.now()));
    }

    /** Stable error body for validated inbox requests. */
    public record ApiError(int status, String error, String message, Instant timestamp) {
    }
}
