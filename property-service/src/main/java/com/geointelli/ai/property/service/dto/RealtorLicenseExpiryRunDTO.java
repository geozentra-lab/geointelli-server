package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;

/**
 * Result of one realtor-license expiry run (SCRUM-204): GeoZentra-granted realtor roles whose holder no longer has a
 * verified license valid on {@code checkedOn}.
 *
 * @param checkedOn  the day (UTC) the licenses were checked against
 * @param candidates users found without a valid license
 * @param revoked    realtor roles revoked
 * @param failed     revocations that failed (Keycloak unreachable); they are retried by the next run
 */
public record RealtorLicenseExpiryRunDTO(LocalDate checkedOn, int candidates, int revoked, int failed) {
}
