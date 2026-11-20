package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RealtorLicenseDecisionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseReviewDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

/**
 * Admin side of realtor license verification (SCRUM-199): review queue and manual decisions.
 *
 * <p>The admin checks the state registry by hand (for Florida, the DBPR licensee search) and records the
 * outcome here. A decision is final and recorded once per submission: it stores the reviewer (the admin
 * behind the bearer token), the decision time, the registry source and, for a rejection, the reason the
 * applicant will read through {@link RealtorLicenseVerificationService}. An inconclusive check is not a
 * decision: the admin leaves the row PENDING_REVIEW. Decisions made here always have decision method MANUAL;
 * the automatic-check columns of the row are never written by this service.
 *
 * <p>Authorisation (Keycloak realm role admin on /api/admin/**) is enforced by the security configuration,
 * not here.
 */
public interface RealtorLicenseReviewService {

    /**
     * Pages through submissions, newest first.
     *
     * @param status only submissions in this status; null for every status
     * @param page   zero-based page number
     * @param size   page size, 1 to 100
     * @return the page; empty content when nothing matches
     * @throws IllegalArgumentException (400) when page is negative or size is out of range
     */
    PageResponse<RealtorLicenseReviewDTO> list(RealtorLicenseVerificationStatus status, int page, int size);

    /**
     * Reads one submission in full, whatever its status.
     *
     * @param id submission ID
     * @return the submission with applicant, decision and automatic-check details
     * @throws com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException (404) when unknown
     */
    RealtorLicenseReviewDTO get(Long id);

    /**
     * Approves a pending submission: the registry shows a matching name and an active license. The row becomes
     * VERIFIED with decision method MANUAL, the current admin as reviewer, the decision time and the registry
     * source from the request.
     *
     * @param id      submission ID
     * @param request registry source; must not carry a rejection reason
     * @return the decided submission
     * @throws com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException (404) when unknown
     * @throws IllegalArgumentException (400) when a rejection reason is given
     * @throws org.springframework.web.server.ResponseStatusException 409 when the submission is no longer
     *         PENDING_REVIEW (already approved or rejected, possibly by another admin a moment ago), or when the
     *         submitted license expiration date is before today (UTC): the license expired while pending
     */
    RealtorLicenseReviewDTO approve(Long id, RealtorLicenseDecisionRequest request);

    /**
     * Rejects a pending submission with a reason the applicant will see. The row becomes REJECTED with decision
     * method MANUAL, the current admin as reviewer, the decision time, the registry source and the reason. The
     * applicant may then submit again (SCRUM-200); this row stays as history.
     *
     * @param id      submission ID
     * @param request registry source and a non-blank rejection reason
     * @return the decided submission
     * @throws com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException (404) when unknown
     * @throws IllegalArgumentException (400) when the rejection reason is missing or blank
     * @throws org.springframework.web.server.ResponseStatusException 409 when the submission is no longer
     *         PENDING_REVIEW
     */
    RealtorLicenseReviewDTO reject(Long id, RealtorLicenseDecisionRequest request);
}
