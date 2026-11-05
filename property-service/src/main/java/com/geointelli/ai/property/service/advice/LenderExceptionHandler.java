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
import com.geointelli.ai.property.service.controller.LenderApplicationController;
import com.geointelli.ai.property.service.controller.LenderOrganizationController;

/** Never expose tenant data or SQL details for concurrent writes or reference changes. */
@RestControllerAdvice(assignableTypes = {LenderApplicationController.class, LenderOrganizationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LenderExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> conflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder().status(409).error("Conflict")
            .message("The lender record conflicts with an existing record or a reference changed; refresh and retry")
            .timestamp(Instant.now()).build());
    }
}

