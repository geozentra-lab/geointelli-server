package com.geointelli.ai.notification.service.dto;

/**
 * Validated event and canonical fingerprint; identical JSON object key order is
 * not required.
 */
public record DecodedAlertEventDTO(AlertEventEnvelopeDTO envelope, NewMatchPayloadDTO payload, String fingerprint) {
}
