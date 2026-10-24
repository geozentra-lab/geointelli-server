package com.geointelli.ai.property.service.advice;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.geointelli.ai.property.service.controller.PortfolioController;
import com.geointelli.ai.property.service.controller.PortfolioHoldingController;

/** Reports concurrent portfolio changes without exposing SQL or database constraint details. */
@RestControllerAdvice(assignableTypes = {PortfolioController.class, PortfolioHoldingController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PortfolioExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class,
        ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder()
            .status(409).error("Conflict")
            .message("The portfolio or holding changed or conflicts with an existing record; refresh and retry")
            .timestamp(Instant.now()).build());
    }
}
