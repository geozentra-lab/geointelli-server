package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Contact details are shared only with the selected professional, following explicit consent. */
public record PropertyContactRequest(
    @NotNull @Positive Long propertyId, @NotNull @Positive Long professionalUserId,
    @NotBlank @Size(max = 120) String contactName,
    @Email @Size(max = 254) String contactEmail,
    @Pattern(regexp = "\\+?[0-9][0-9 ().-]{5,38}") @Size(max = 40) String contactPhone,
    @Size(max = 2000) String message, @NotNull Boolean consent,
    @NotBlank @Size(max = 80) String consentVersion) {
    @AssertTrue(message = "At least one contact email or phone is required")
    @JsonIgnore
    public boolean isContactProvided() {
        return (contactEmail != null && !contactEmail.isBlank()) || (contactPhone != null && !contactPhone.isBlank());
    }
}

