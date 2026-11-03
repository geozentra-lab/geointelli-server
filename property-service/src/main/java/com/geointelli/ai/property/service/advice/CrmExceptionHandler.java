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
import com.geointelli.ai.property.service.controller.CrmActivityController;
import com.geointelli.ai.property.service.controller.CrmClientController;
import com.geointelli.ai.property.service.controller.CrmLeadController;

/** Keeps concurrent duplicate/reference/locking errors out of HTTP responses. */
@RestControllerAdvice(assignableTypes = {CrmClientController.class, CrmLeadController.class, CrmActivityController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrmExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> conflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder().status(409).error("Conflict")
            .message("The CRM record conflicts with an existing record or changed concurrently; refresh and retry")
            .timestamp(Instant.now()).build());
    }
}
