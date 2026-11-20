package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.DataQualityIssueReviewDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewEventDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewRequest;
import com.geointelli.ai.property.service.entity.DataQualityIssueReview;
import com.geointelli.ai.property.service.enums.DataQualityReviewStatus;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.exception.DataQualityEntityNotFoundException;
import com.geointelli.ai.property.service.repository.DataQualityEntityRow;
import com.geointelli.ai.property.service.repository.DataQualityIssueReviewRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.DataQualityReviewService;
import lombok.RequiredArgsConstructor;

/**
 * Appends review actions to {@code data_quality_issue_reviews} and reads them back (SCRUM-190).
 *
 * <p>A reference is validated in two cheap steps: its shape (known rule, the rule's entity type, a positive ID)
 * and the existence of the entity by primary key. The scanner rule is not re-run here: that would scan a large
 * table on every click, and the scanner report already says whether a finding is still reported. Two admins
 * acting on the same finding at the same time are serialised by an advisory lock on the reference, so the
 * second one sees the first one's row and gets 409 if its move is no longer allowed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DataQualityReviewServiceImpl implements DataQualityReviewService {

    private final DataQualityIssueReviewRepository repository;
    private final CurrentUserService currentUserService;

    /** A parsed, well-formed reference. */
    private record Reference(String value, DataQualityRule rule, String entityType, Long entityId) {
    }

    @Override
    public DataQualityIssueReviewDTO review(String reference, DataQualityReviewRequest request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        DataQualityReviewStatus target = request.status();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (target.requiresReason() && reason == null) {
            throw new IllegalArgumentException("reason is required when moving a finding to " + target);
        }
        Reference ref = parse(reference);
        Long propertyId = requireEntity(ref).getPropertyId();
        Long reviewerId = currentUserService.getCurrentUserEntity().getId();

        repository.lockReference(ref.value());
        DataQualityReviewStatus current = repository.findFirstByIssueReferenceOrderByIdDesc(ref.value())
                .map(DataQualityIssueReview::getToStatus)
                .orElse(DataQualityReviewStatus.OPEN);
        if (!current.canMoveTo(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finding " + ref.value() + " is " + current
                    + " and cannot move to " + target + "; allowed: " + current.allowedTargets());
        }

        DataQualityIssueReview row = new DataQualityIssueReview();
        row.setIssueReference(ref.value());
        row.setRule(ref.rule());
        row.setEntityType(ref.entityType());
        row.setEntityId(ref.entityId());
        row.setPropertyId(propertyId);
        row.setFromStatus(current);
        row.setToStatus(target);
        row.setReason(reason);
        row.setReviewedByUserId(reviewerId);
        // PostgreSQL keeps microseconds; truncating makes the response equal to what GET returns later
        row.setReviewedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.saveAndFlush(row);
        return toDTO(ref, propertyId);
    }

    @Override
    @Transactional(readOnly = true)
    public DataQualityIssueReviewDTO get(String reference) {
        Reference ref = parse(reference);
        return toDTO(ref, requireEntity(ref).getPropertyId());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, DataQualityReviewEventDTO> latestByReference(Collection<String> references) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        }
        return repository.findLatestByIssueReferenceIn(references).stream()
                .collect(Collectors.toMap(DataQualityIssueReview::getIssueReference,
                        DataQualityReviewServiceImpl::toEvent, (a, b) -> a));
    }

    /** Splits RULE:entityType:entityId and checks each part; the entity type must be the rule's own. */
    private static Reference parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference is required, as RULE:entityType:entityId");
        }
        String[] parts = reference.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("reference must be RULE:entityType:entityId, got " + reference);
        }
        DataQualityRule rule;
        try {
            rule = DataQualityRule.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown data-quality rule " + parts[0]);
        }
        if (!rule.getEntityType().equals(parts[1])) {
            throw new IllegalArgumentException("rule " + rule + " reports " + rule.getEntityType()
                    + " findings, not " + parts[1]);
        }
        long id;
        try {
            id = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("entity ID must be a number, got " + parts[2]);
        }
        if (id <= 0) {
            throw new IllegalArgumentException("entity ID must be positive");
        }
        String value = rule.name() + ":" + rule.getEntityType() + ":" + id;
        return new Reference(value, rule, rule.getEntityType(), id);
    }

    /** The entity the reference points to, by primary key; 404 when it does not exist. */
    private DataQualityEntityRow requireEntity(Reference ref) {
        Optional<DataQualityEntityRow> row = switch (ref.entityType()) {
            case "PROPERTY" -> repository.findProperty(ref.entityId());
            case "SALE" -> repository.findSale(ref.entityId());
            case "ASSESSMENT" -> repository.findAssessment(ref.entityId());
            default -> Optional.empty();
        };
        return row.orElseThrow(() -> new DataQualityEntityNotFoundException(
                ref.entityType() + " " + ref.entityId() + " of finding " + ref.value() + " does not exist"));
    }

    private DataQualityIssueReviewDTO toDTO(Reference ref, Long propertyId) {
        List<DataQualityReviewEventDTO> history = repository.findByIssueReferenceOrderByIdAsc(ref.value()).stream()
                .map(DataQualityReviewServiceImpl::toEvent)
                .toList();
        DataQualityReviewEventDTO latest = history.isEmpty() ? null : history.get(history.size() - 1);
        return new DataQualityIssueReviewDTO(ref.value(), ref.rule(), ref.rule().getCategory(), ref.entityType(),
                ref.entityId(), propertyId,
                latest == null ? DataQualityReviewStatus.OPEN : latest.toStatus(),
                latest == null ? null : latest.reviewedByUserId(),
                latest == null ? null : latest.reviewedAt(),
                latest == null ? null : latest.reason(),
                history);
    }

    private static DataQualityReviewEventDTO toEvent(DataQualityIssueReview row) {
        return new DataQualityReviewEventDTO(row.getId(), row.getFromStatus(), row.getToStatus(), row.getReason(),
                row.getReviewedByUserId(), row.getReviewedAt());
    }
}
