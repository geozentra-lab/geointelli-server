package com.geointelli.ai.notification.service.dto;

/**
 * Validated event and canonical fingerprint; identical JSON object key order is
 * not required.
 */
public record DecodedAlertEventDTO(AlertEventEnvelopeDTO envelope, NewMatchPayloadDTO payload, String fingerprint,
        ContactRequestPayloadDTO contactPayload, ValuationChangePayloadDTO valuationPayload) {
    public DecodedAlertEventDTO(AlertEventEnvelopeDTO envelope, NewMatchPayloadDTO payload, String fingerprint) {
        this(envelope, payload, fingerprint, null, null);
    }

    /** Keeps the existing contact-event construction contract. */
    public DecodedAlertEventDTO(AlertEventEnvelopeDTO envelope, NewMatchPayloadDTO payload, String fingerprint,
            ContactRequestPayloadDTO contactPayload) {
        this(envelope, payload, fingerprint, contactPayload, null);
    }
}
