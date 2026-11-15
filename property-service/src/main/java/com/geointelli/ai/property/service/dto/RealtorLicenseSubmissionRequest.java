package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/users/me/realtor-license. The owner is never part of the body; it is the
 * authenticated user. Only Florida (FL) is accepted for now; other states are refused with 400.
 */
public record RealtorLicenseSubmissionRequest(
        @NotBlank @Size(max = 160) String licenseHolderName,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter state code") String licensingState,
        @NotBlank @Size(max = 40) String licenseNumber,
        /** Optional; when present it must not be in the past. Format yyyy-MM-dd. */
        LocalDate licenseExpirationDate,
        @Size(max = 160) String brokerageName,
        @Email @Size(max = 254) String workEmail) {
}
