package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A deal requires an owned contact and an existing property; lead linkage is optional. */
public record CrmTransactionCreateRequest(@NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description, @NotNull @Positive Long clientId,
        @Positive Long leadId, @NotNull @Positive Long propertyId, LocalDate expectedCloseDate) {
}
