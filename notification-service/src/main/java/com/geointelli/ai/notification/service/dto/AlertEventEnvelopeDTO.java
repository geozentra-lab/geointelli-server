package com.geointelli.ai.notification.service.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Independent v1 wire contract; no dependency on property-service Java classes
 * or type headers.
 */
public record AlertEventEnvelopeDTO(
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank @Size(max = 40) String source,
        @NotNull Integer eventVersion,
        @NotBlank @Size(max = 40) String eventType,
        @NotBlank @Size(max = 255) String userId,
        @NotNull @Positive Long alertRuleId,
        @Positive Long savedSearchId, @Positive Long propertyId,
        @NotBlank @Size(max = 160) String sourceChangeId,
        @NotNull Instant occurredAt, @NotNull JsonNode payload) {
}
