package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import com.geointelli.ai.property.service.enums.DataQualityReviewStatus;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One review action on a data-quality finding (table data_quality_issue_reviews, V41, SCRUM-190).
 *
 * <p>The log is append-only: a row is inserted once and never updated, so every column is read-only after
 * creation. The newest row of a reference is the current state of the finding; a finding without rows is
 * OPEN. The finding itself is not stored anywhere: the scanner of SCRUM-189 recomputes it from the
 * reference {@code rule:entityType:entityId}.
 */
@Entity
@Table(name = "data_quality_issue_reviews")
@Getter
@Setter
public class DataQualityIssueReview extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reproducible reference of the finding, exactly as the scanner returns it. */
    @Column(name = "issue_reference", nullable = false, updatable = false, length = 80)
    private String issueReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private DataQualityRule rule;

    /** PROPERTY, SALE or ASSESSMENT, always the entity type of the rule. */
    @Column(name = "entity_type", nullable = false, updatable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    /** Property concerned by the finding, copied at review time; no foreign key. */
    @Column(name = "property_id", updatable = false)
    private Long propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 20)
    private DataQualityReviewStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private DataQualityReviewStatus toStatus;

    /** Why the reviewer moved the finding; required unless toStatus is IN_REVIEW. */
    @Column(updatable = false, length = 500)
    private String reason;

    /** Admin behind the bearer token (users.id), never taken from the request body. */
    @Column(name = "reviewed_by_user_id", nullable = false, updatable = false)
    private Long reviewedByUserId;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;
}
