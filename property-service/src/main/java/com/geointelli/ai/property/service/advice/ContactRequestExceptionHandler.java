package com.geointelli.ai.property.service.advice;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.geointelli.ai.property.service.controller.ContactRequestController;

/** Keeps CRM reference and concurrency details out of public contact-request responses. */
@RestControllerAdvice(assignableTypes = ContactRequestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContactRequestExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> conflict(Exception error) {
        return ResponseEntity.status(409).body(ApiError.builder().status(409).error("Conflict")
            .message("A contact reference changed or conflicts with an existing record; retry with the same Idempotency-Key")
            .timestamp(Instant.now()).build());
    }
}

