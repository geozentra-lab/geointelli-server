package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A client needs a name and at least one valid email or phone number. */
public record CrmClientCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "\\+?[0-9][0-9 ().-]{5,38}") @Size(max = 40) String phone,
        @Size(max = 2000) String notes) {
}
