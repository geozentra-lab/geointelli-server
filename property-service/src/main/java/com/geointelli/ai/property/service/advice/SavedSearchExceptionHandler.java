package com.geointelli.ai.property.service.advice;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.geointelli.ai.property.service.controller.SavedSearchController;

/** Handles concurrent edits and deletion without exposing database details. */
@RestControllerAdvice(assignableTypes = SavedSearchController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SavedSearchExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder().status(409).error("Conflict")
            .message("The saved search changed or conflicts with an existing record; refresh and retry")
            .timestamp(Instant.now()).build());
    }
}
