package com.geointelli.ai.notification.service.dto;

/**
 * Only explicit throttling is retryable; uncertain acceptance is retained for
 * review.
 */
public record EmailSendResultDTO(Outcome outcome, String providerMessageId, String errorCode) {
    public enum Outcome {
        ACCEPTED, RETRYABLE_REJECTION, REJECTED, UNKNOWN
    }
}
