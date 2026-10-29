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
import com.geointelli.ai.property.service.controller.AlertRuleController;
import com.geointelli.ai.property.service.controller.AlertMonitoringController;

/** Reports duplicate rules and concurrent target deletion without leaking database internals. */
@RestControllerAdvice(assignableTypes = {AlertRuleController.class, AlertMonitoringController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AlertRuleExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder()
            .status(409).error("Conflict")
            .message("The alert rule already exists or its target changed; refresh and retry")
            .timestamp(Instant.now()).build());
    }
}
