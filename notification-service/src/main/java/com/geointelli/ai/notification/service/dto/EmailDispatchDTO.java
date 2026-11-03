package com.geointelli.ai.notification.service.dto;

/**
 * Immutable snapshot committed before contacting the email provider. Never
 * returned in the public API.
 */
public record EmailDispatchDTO(long id, int attempt, String recipient, String subject, String body) {
}
