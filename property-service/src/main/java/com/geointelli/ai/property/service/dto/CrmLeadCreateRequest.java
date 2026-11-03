package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Source is a user-supplied label (for example MANUAL, WEBSITE or REFERRAL), not a verified provider. */
public record CrmLeadCreateRequest(@NotNull @Positive Long clientId, @Positive Long propertyId,
        @NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 80) String source,
        @Size(max = 2000) String notes) {
}
