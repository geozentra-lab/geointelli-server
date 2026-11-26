package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.ApplicationRoles;
import com.geointelli.ai.property.service.dto.RealtorLicenseSubmissionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseVerificationDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.OnboardingService;
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
    /** Latest accepted expiration date: a four-digit year (yyyy-MM-dd), which PostgreSQL can always store. */
    private static final LocalDate MAX_EXPIRATION_DATE = LocalDate.of(9999, 12, 31);

    private final RealtorLicenseVerificationRepository verifications;
    private final CurrentUserService currentUser;
    private final OnboardingService accountRoles;

    @Override
    public RealtorLicenseVerificationDTO submit(RealtorLicenseSubmissionRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        User user = currentUser.getCurrentUserEntity();
        Long owner = user.getId();
        String state = request.licensingState().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATES.contains(state)) {
            throw new IllegalArgumentException("licensingState " + state + " is not supported; supported: " + SUPPORTED_STATES);
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (request.licenseExpirationDate() != null && request.licenseExpirationDate().isBefore(today)) {
            throw new IllegalArgumentException("licenseExpirationDate must not be in the past");
        }
        if (request.licenseExpirationDate() != null && request.licenseExpirationDate().isAfter(MAX_EXPIRATION_DATE)) {
            throw new IllegalArgumentException("licenseExpirationDate must not be after " + MAX_EXPIRATION_DATE);
        }
        // a pending submission, or a verified license that has not expired, blocks a new one (SCRUM-204: an expired
        // verified license no longer blocks, so the renewed license can be submitted)
        if (verifications.existsBlockingSubmission(owner, RealtorLicenseVerificationStatus.PENDING_REVIEW,
                RealtorLicenseVerificationStatus.VERIFIED, today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A license submission is already pending, or "
                + "a verified license is still valid; a new one is allowed after a rejection or once it has expired");
        }
        // one professional role per account (SCRUM-204): an investor or lender role, or a pending request for one,
        // refuses the license (409)
        accountRoles.requireNoOtherProfessionalRole(user, ApplicationRoles.REALTOR, accountRoles.heldProfessionalRoles(
            user, SecurityContextHolder.getContext().getAuthentication()));
        RealtorLicenseVerification row = new RealtorLicenseVerification();
        row.setUserId(owner);
        row.setLicenseHolderName(request.licenseHolderName().trim());
        row.setLicensingState(state);
        row.setLicenseNumber(request.licenseNumber().trim());
        row.setLicenseExpirationDate(request.licenseExpirationDate());
        row.setBrokerageName(blankToNull(request.brokerageName()));
        row.setWorkEmail(blankToNull(request.workEmail()));
        row.setStatus(RealtorLicenseVerificationStatus.PENDING_REVIEW);
        // PostgreSQL keeps microseconds; truncating here makes the 201 body match what GET returns later
        row.setSubmittedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // saveAndFlush so the partial unique index (one pending per user) fails here, inside this method,
        // when two submissions race; RealtorLicenseExceptionHandler turns that into a 409.
        RealtorLicenseVerificationDTO saved = toDTO(verifications.saveAndFlush(row));
        // choosing realtor answers the onboarding page, whatever the decision
        accountRoles.markCompleted(user);
        return saved;
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
