package com.geointelli.ai.property.service.dto;

import java.time.Instant;

/** SENT means broker-confirmed publication, not email or inbox delivery. */
public record ContactResponseDTO(Long id, Long propertyId, Long professionalUserId, Long leadId,
    String status, String deliveryStatus, Instant submittedAt, String contactName, String contactEmail,
    String contactPhone, String message, String consentVersion) {}

