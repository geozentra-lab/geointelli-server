package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import com.geointelli.ai.property.service.entity.RealtorLicenseAutoCheckResult;
import com.geointelli.ai.property.service.entity.RealtorLicenseDecisionMethod;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

/**
 * Admin-facing view of one realtor license submission (SCRUM-199). Unlike
 * {@link RealtorLicenseVerificationDTO}, it names the applicant (user ID and account e-mail), the reviewer,
 * the registry source, the decision method and the automatic check, because the admin needs all of it to
 * check the state registry and to audit past decisions. Served only under /api/admin.
 *
 * @param id                    submission ID
 * @param applicantUserId       ID of the user who submitted (users.id)
 * @param applicantEmail        account e-mail of that user, null if the account has none
 * @param licenseHolderName     name as submitted, to compare with the registry
 * @param licensingState        two-letter state (FL)
 * @param licenseNumber         license number as submitted
 * @param licenseExpirationDate expiration date as submitted, may be null
 * @param brokerageName         brokerage as submitted, may be null
 * @param workEmail             work e-mail as submitted, may be null
 * @param status                PENDING_REVIEW, VERIFIED or REJECTED
 * @param decisionMethod        MANUAL or AUTOMATIC once decided, null while pending
 * @param reviewedByUserId      admin who decided (MANUAL), null while pending or when AUTOMATIC
 * @param decidedAt             when the decision was recorded, null while pending
 * @param registrySource        registry the decision was checked against, null while pending
 * @param rejectionReason       applicant-visible reason, only when REJECTED
 * @param autoCheckResult       MATCH, NO_MATCH or INCONCLUSIVE if an automatic check ran, else null
 * @param autoCheckedAt         when that check ran, else null
 * @param autoCheckSource       registry the automatic check used, else null
 * @param autoCheckNote         free-text detail of the automatic check, else null
 * @param submittedAt           when the applicant submitted
 */
public record RealtorLicenseReviewDTO(Long id, Long applicantUserId, String applicantEmail,
        String licenseHolderName, String licensingState, String licenseNumber, LocalDate licenseExpirationDate,
        String brokerageName, String workEmail, RealtorLicenseVerificationStatus status,
        RealtorLicenseDecisionMethod decisionMethod, Long reviewedByUserId, Instant decidedAt, String registrySource,
        String rejectionReason, RealtorLicenseAutoCheckResult autoCheckResult, Instant autoCheckedAt,
        String autoCheckSource, String autoCheckNote, Instant submittedAt) {
}
