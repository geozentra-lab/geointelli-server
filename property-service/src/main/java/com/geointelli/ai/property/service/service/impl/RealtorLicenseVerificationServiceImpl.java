package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.RealtorLicenseSubmissionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseVerificationDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import lombok.RequiredArgsConstructor;

/**
 * Applicant side of realtor license verification. Every query is scoped by the owner taken from
 * the bearer token; the request body never names a user. Decisions are out of scope (SCRUM-199).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RealtorLicenseVerificationServiceImpl implements RealtorLicenseVerificationService {
    /** States whose registry the team can check today; extend when another registry is supported. */
    private static final Set<String> SUPPORTED_STATES = Set.of("FL");
    /** A submission in one of these statuses blocks a new one; only REJECTED allows resubmission. */
    private static final List<RealtorLicenseVerificationStatus> BLOCKING_STATUSES =
        List.of(RealtorLicenseVerificationStatus.PENDING_REVIEW, RealtorLicenseVerificationStatus.VERIFIED);

    private final RealtorLicenseVerificationRepository verifications;
    private final CurrentUserService currentUser;

    @Override
    public RealtorLicenseVerificationDTO submit(RealtorLicenseSubmissionRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        Long owner = currentUser.getCurrentUserEntity().getId();
        String state = request.licensingState().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATES.contains(state)) {
            throw new IllegalArgumentException("licensingState " + state + " is not supported; supported: " + SUPPORTED_STATES);
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (request.licenseExpirationDate() != null && request.licenseExpirationDate().isBefore(today)) {
            throw new IllegalArgumentException("licenseExpirationDate must not be in the past");
        }
        if (verifications.existsByUserIdAndStatusIn(owner, BLOCKING_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A license submission is already pending or verified; a new one is allowed only after a rejection");
        }
        RealtorLicenseVerification row = new RealtorLicenseVerification();
        row.setUserId(owner);
        row.setLicenseHolderName(request.licenseHolderName().trim());
        row.setLicensingState(state);
        row.setLicenseNumber(request.licenseNumber().trim());
        row.setLicenseExpirationDate(request.licenseExpirationDate());
        row.setBrokerageName(blankToNull(request.brokerageName()));
        row.setWorkEmail(blankToNull(request.workEmail()));
        row.setStatus(RealtorLicenseVerificationStatus.PENDING_REVIEW);
        row.setSubmittedAt(Instant.now());
        // saveAndFlush so the partial unique index (one pending per user) fails here, inside this method,
        // when two submissions race; the CRM-style advice turns that into a 409.
        return toDTO(verifications.saveAndFlush(row));
    }

    @Override
    @Transactional(readOnly = true)
    public RealtorLicenseVerificationDTO getLatest() {
        Long owner = currentUser.getCurrentUserEntity().getId();
        return verifications.findFirstByUserIdOrderByIdDesc(owner).map(this::toDTO)
            .orElseThrow(() -> new RealtorLicenseNotFoundException("No realtor license submission yet"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RealtorLicenseVerificationDTO> getHistory() {
        Long owner = currentUser.getCurrentUserEntity().getId();
        return verifications.findByUserIdOrderByIdDesc(owner).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RealtorLicenseVerificationStatus> latestStatusOf(Long userId) {
        return verifications.findFirstByUserIdOrderByIdDesc(userId).map(RealtorLicenseVerification::getStatus);
    }

    /** Owner-facing view: never the reviewer, the registry source or the automatic check. */
    private RealtorLicenseVerificationDTO toDTO(RealtorLicenseVerification row) {
        return new RealtorLicenseVerificationDTO(row.getId(), row.getLicenseHolderName(), row.getLicensingState(),
            row.getLicenseNumber(), row.getLicenseExpirationDate(), row.getBrokerageName(), row.getWorkEmail(),
            row.getStatus(), row.getRejectionReason(), row.getSubmittedAt(), row.getDecidedAt());
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
