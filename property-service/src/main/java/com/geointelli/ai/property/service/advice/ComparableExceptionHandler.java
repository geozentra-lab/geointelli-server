package com.geointelli.ai.property.service.advice;

import java.time.Instant;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geointelli.ai.property.service.controller.ComparableCandidateController;
import com.geointelli.ai.property.service.controller.ComparableController;

/** Returns a safe retryable response when a bounded comparable read times out. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ComparableController.class, ComparableCandidateController.class})
public class ComparableExceptionHandler {
    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<ApiError> handleTimeout(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.builder()
            .status(503).error("Service Unavailable")
            .message("Comparable retrieval timed out. Please try again later.")
            .timestamp(Instant.now()).build());
    }
}
