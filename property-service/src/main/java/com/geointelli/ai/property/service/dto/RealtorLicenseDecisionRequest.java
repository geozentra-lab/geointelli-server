package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of an admin decision on a realtor license submission (SCRUM-199), shared by the approve and reject
 * endpoints. The service decides what the reason means: mandatory on reject (the applicant reads it),
 * refused on approve. The lengths match the columns of V37 (registry_source 200, rejection_reason 500).
 *
 * @param registrySource  registry the admin checked, for example "Florida DBPR licensee search"; required
 * @param rejectionReason applicant-visible reason; required when rejecting, must be absent when approving
 */
public record RealtorLicenseDecisionRequest(
        @NotBlank(message = "registrySource is required") @Size(max = 200) String registrySource,
        @Size(max = 500) String rejectionReason) {
}
