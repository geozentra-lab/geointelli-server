package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RealtorLicenseDecisionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseReviewDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseDecisionMethod;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.RealtorLicenseNotFoundException;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.RealtorLicenseReviewService;
import lombok.RequiredArgsConstructor;

/**
 * Admin side of realtor license verification: review queue and manual decisions (SCRUM-199).
 *
 * <p>A decision loads the row with a pessimistic lock, so two admins deciding the same submission at the same
 * time are serialised and the second one is refused with 409. The reviewer is always the admin behind the
 * bearer token. Only the decision columns of V37 are written (status, decision_method = MANUAL, reviewer,
 * decided_at, registry_source, rejection_reason); the submitted details and the automatic-check columns are
 * never touched. The CHECK constraints of V37 are the last line of defence.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RealtorLicenseReviewServiceImpl implements RealtorLicenseReviewService {
    private static final int MAX_PAGE_SIZE = 100;

    private final RealtorLicenseVerificationRepository repository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RealtorLicenseReviewDTO> list(RealtorLicenseVerificationStatus status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<RealtorLicenseVerification> rows = status == null
            ? repository.findAllByOrderByIdDesc(pageable)
            : repository.findByStatusOrderByIdDesc(status, pageable);
        // one query for the applicant e-mails of the whole page instead of one per row
        Map<Long, String> emails = userRepository.findAllById(
                rows.getContent().stream().map(RealtorLicenseVerification::getUserId).collect(Collectors.toSet()))
            .stream().filter(u -> u.getEmail() != null)
            .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        return PageResponse.from(rows.map(row -> toDTO(row, emails.get(row.getUserId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public RealtorLicenseReviewDTO get(Long id) {
        RealtorLicenseVerification row = repository.findById(id)
            .orElseThrow(() -> new RealtorLicenseNotFoundException("Realtor license submission " + id + " not found"));
        return toDTO(row, applicantEmail(row));
    }

    @Override
    public RealtorLicenseReviewDTO approve(Long id, RealtorLicenseDecisionRequest request) {
        requireRequest(request);
        if (request.rejectionReason() != null && !request.rejectionReason().isBlank()) {
            throw new IllegalArgumentException("rejectionReason is only accepted when rejecting");
        }
        return decide(id, RealtorLicenseVerificationStatus.VERIFIED, request.registrySource(), null);
    }

    @Override
    public RealtorLicenseReviewDTO reject(Long id, RealtorLicenseDecisionRequest request) {
        requireRequest(request);
        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required when rejecting; the applicant will read it");
        }
        return decide(id, RealtorLicenseVerificationStatus.REJECTED, request.registrySource(),
            request.rejectionReason().trim());
    }

    /**
     * Records a manual decision on a pending submission. The row is locked for the rest of the transaction, so a
     * concurrent decision waits and then fails the PENDING_REVIEW check below instead of overwriting ours.
     */
    private RealtorLicenseReviewDTO decide(Long id, RealtorLicenseVerificationStatus outcome, String registrySource,
                                           String rejectionReason) {
        Long reviewerId = currentUserService.getCurrentUserEntity().getId();
        RealtorLicenseVerification row = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RealtorLicenseNotFoundException("Realtor license submission " + id + " not found"));
        if (row.getStatus() != RealtorLicenseVerificationStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Submission " + id + " was already decided (" + row.getStatus() + "); a decision is recorded only once");
        }
        row.setStatus(outcome);
        row.setDecisionMethod(RealtorLicenseDecisionMethod.MANUAL);
        row.setReviewedByUserId(reviewerId);
        // PostgreSQL keeps microseconds; truncating here makes the response match what GET returns later
        row.setDecidedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        row.setRegistrySource(registrySource.trim());
        row.setRejectionReason(rejectionReason);
        RealtorLicenseVerification saved = repository.saveAndFlush(row);
        return toDTO(saved, applicantEmail(saved));
    }

    private static void requireRequest(RealtorLicenseDecisionRequest request) {
        if (request == null || request.registrySource() == null || request.registrySource().isBlank()) {
            throw new IllegalArgumentException("registrySource is required");
        }
    }

    private String applicantEmail(RealtorLicenseVerification row) {
        return userRepository.findById(row.getUserId()).map(User::getEmail).orElse(null);
    }

    private static RealtorLicenseReviewDTO toDTO(RealtorLicenseVerification row, String applicantEmail) {
        return new RealtorLicenseReviewDTO(row.getId(), row.getUserId(), applicantEmail, row.getLicenseHolderName(),
            row.getLicensingState(), row.getLicenseNumber(), row.getLicenseExpirationDate(), row.getBrokerageName(),
            row.getWorkEmail(), row.getStatus(), row.getDecisionMethod(), row.getReviewedByUserId(), row.getDecidedAt(),
            row.getRegistrySource(), row.getRejectionReason(), row.getAutoCheckResult(), row.getAutoCheckedAt(),
            row.getAutoCheckSource(), row.getAutoCheckNote(), row.getSubmittedAt());
    }
}
