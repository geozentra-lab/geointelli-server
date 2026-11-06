package com.geointelli.ai.notification.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Consented contact details delivered only to the producer-selected professional's inbox. */
public record ContactRequestPayloadDTO(@NotNull @Positive Long contactRequestId, @NotNull @Positive Long leadId,
        @NotNull @Positive Long propertyId, @NotBlank @Size(max = 120) String contactName,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = "\\+?[0-9][0-9 ().-]{5,38}") @Size(max = 40) String contactPhone,
        @Size(max = 2000) String message) {}
