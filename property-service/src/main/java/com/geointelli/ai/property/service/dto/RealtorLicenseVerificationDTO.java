package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

/**
 * Owner-facing view of one realtor license submission. Exposes the submitted details, the status,
 * the rejection reason and the dates; never the reviewer, the registry source or the automatic check.
 */
public record RealtorLicenseVerificationDTO(Long id, String licenseHolderName, String licensingState,
        String licenseNumber, LocalDate licenseExpirationDate, String brokerageName, String workEmail,
        RealtorLicenseVerificationStatus status, String rejectionReason, Instant submittedAt, Instant decidedAt) {
}
