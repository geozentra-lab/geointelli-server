package com.geointelli.ai.property.service.dto;

import java.time.Instant;

/** Versioned wire contract shared by value, not by Java dependency, with notification-service. */
public record ContactRequestEventDTO(String eventId, String source, int eventVersion, String eventType,
        String userId, Long alertRuleId, Long savedSearchId, Long propertyId,
        String sourceChangeId, Instant occurredAt, ContactPayload payload) {
    public record ContactPayload(Long contactRequestId, Long leadId, Long propertyId, String contactName,
        String contactEmail, String contactPhone, String message) {}
}

