package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.entity.AlertEventType;

/** Versioned queue contract. Consumers must deduplicate by eventId before creating a notification. */
public record AlertEventEnvelopeDTO(String eventId, String source, int eventVersion, AlertEventType eventType,
    String userId, Long alertRuleId, Long savedSearchId, Long propertyId, String sourceChangeId,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt, JsonNode payload) {
}
