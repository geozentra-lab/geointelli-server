package com.geointelli.ai.property.service.advice;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.geointelli.ai.property.service.controller.RealtorLicenseVerificationController;

/** Turns the "one pending submission per user" database rule into a 409 when two submissions race. */
@RestControllerAdvice(assignableTypes = RealtorLicenseVerificationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RealtorLicenseExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder().status(409).error("Conflict")
            .message("A license submission is already pending; read GET /api/users/me/realtor-license")
            .timestamp(Instant.now()).build());
    }
}
