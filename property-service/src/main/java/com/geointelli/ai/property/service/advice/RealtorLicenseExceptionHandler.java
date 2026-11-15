package com.geointelli.ai.property.service.advice;

import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.geointelli.ai.property.service.controller.RealtorLicenseVerificationController;

/**
 * Database refusals of a license submission. The "one pending submission per user" index (V37) means two
 * submissions raced: 409. Anything else the database refuses is a value that passed validation but cannot be
 * stored (for example a NUL character in a text field): 400, never the misleading "already pending".
 */
@RestControllerAdvice(assignableTypes = RealtorLicenseVerificationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RealtorLicenseExceptionHandler {
    static final String ONE_PENDING_INDEX = "uq_realtor_license_one_pending";

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> refused(DataIntegrityViolationException error) {
        if (violatesOnePending(error)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.builder().status(409).error("Conflict")
                .message("A license submission is already pending; read GET /api/users/me/realtor-license")
                .timestamp(Instant.now()).build());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.builder().status(400).error("Bad Request")
            .message("The license submission contains a value that cannot be stored; check the fields and retry")
            .timestamp(Instant.now()).build());
    }

    /** True when the violated constraint, as reported by Hibernate, is the one-pending-per-user index. */
    private static boolean violatesOnePending(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && ONE_PENDING_INDEX.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
