package com.geointelli.ai.notification.service.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/** An owned inbox item. Internal delivery receipts and recipient identifiers are not exposed. */
public record NotificationDTO(Long id, String eventType, String title, String message,
        Long alertRuleId, Long savedSearchId, Long propertyId, JsonNode payload,
        Instant occurredAt, Instant createdAt,
        @Schema(description = "Null until first marked read; subsequent requests preserve this timestamp.") Instant readAt,
        boolean read) {}
