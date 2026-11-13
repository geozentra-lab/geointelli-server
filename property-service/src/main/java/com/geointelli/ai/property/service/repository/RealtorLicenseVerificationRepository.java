package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

/** Realtor license submissions; owner-facing queries always filter by the authenticated user's ID. */
public interface RealtorLicenseVerificationRepository extends JpaRepository<RealtorLicenseVerification, Long> {
    /** Newest submission of the owner, the one whose status the profile shows. */
    Optional<RealtorLicenseVerification> findFirstByUserIdOrderByIdDesc(Long userId);

    /** All submissions of the owner, newest first; rejected rows are kept as history. */
    List<RealtorLicenseVerification> findByUserIdOrderByIdDesc(Long userId);

    /** True when the owner already has a row in one of the given statuses (PENDING_REVIEW or VERIFIED blocks a new POST). */
    boolean existsByUserIdAndStatusIn(Long userId, List<RealtorLicenseVerificationStatus> statuses);

    /** Admin listing by status, newest first (used by SCRUM-199). */
    Page<RealtorLicenseVerification> findByStatusOrderByIdDesc(RealtorLicenseVerificationStatus status, Pageable pageable);
}
