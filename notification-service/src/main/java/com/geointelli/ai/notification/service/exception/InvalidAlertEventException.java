package com.geointelli.ai.notification.service.exception;

/**
 * Permanent event-contract violation, represented by a stable recovery code.
 */
public class InvalidAlertEventException extends RuntimeException {
    public InvalidAlertEventException(String code) {
        super(code);
    }
}
