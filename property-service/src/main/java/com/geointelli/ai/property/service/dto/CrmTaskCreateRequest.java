package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Associations are optional; a linked transaction or lead supplies its client where omitted. */
public record CrmTaskCreateRequest(@NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description, Instant dueAt, @Positive Long clientId,
        @Positive Long leadId, @Positive Long propertyId, @Positive Long transactionId) {
}
