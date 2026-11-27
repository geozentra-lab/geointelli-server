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

import com.geointelli.ai.property.service.controller.AdminDataQualityController;

/**
 * Answers 503 when an issue page of the data-quality report is not read within its time limit (SCRUM-206), so the
 * request ends instead of holding a database connection; the rule counts never time out a request, they are counted
 * in the background.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminDataQualityController.class)
public class DataQualityExceptionHandler {
    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<ApiError> handleTimeout(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.builder()
            .status(503).error("Service Unavailable")
            .message("The issues of this rule took too long to read and the request was stopped. Please try again "
                + "later.")
            .timestamp(Instant.now()).build());
    }
}
