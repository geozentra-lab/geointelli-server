package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Optional;
import com.geointelli.ai.property.service.dto.RealtorLicenseSubmissionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseVerificationDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

/**
 * Applicant side of realtor license verification (SCRUM-200): submission, own status and history.
 *
 * <p>Every operation is scoped to the authenticated user; there is no way to read or create a
 * submission for someone else. Decisions (approve/reject) belong to SCRUM-199 and are not here.
 */
public interface RealtorLicenseVerificationService {

    /**
     * Creates a new PENDING_REVIEW submission for the current user.
     *
     * @param request submitted details; the licensing state must be FL and the expiration date, if
     *                given, must not be in the past (otherwise {@link IllegalArgumentException}, 400)
     * @return the created submission
     * @throws org.springframework.web.server.ResponseStatusException 409 when the user already has a
     *         PENDING_REVIEW or VERIFIED submission; a new submission is allowed only after a rejection
     */
    RealtorLicenseVerificationDTO submit(RealtorLicenseSubmissionRequest request);

    /**
     * Returns the newest submission of the current user, whatever its status.
     *
     * @throws com.geointelli.ai.property.service.exception.base.ResourceNotFoundException 404 when
     *         the user never submitted a license
     */
    RealtorLicenseVerificationDTO getLatest();

    /** All submissions of the current user, newest first; empty list when none. */
    List<RealtorLicenseVerificationDTO> getHistory();

    /**
     * Status of the newest submission of the given user, for the profile response.
     *
     * @return empty when the user never submitted a license
     */
    Optional<RealtorLicenseVerificationStatus> latestStatusOf(Long userId);
}
