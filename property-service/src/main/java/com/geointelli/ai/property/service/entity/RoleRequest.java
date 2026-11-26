package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A user's request for the lender or investor Keycloak realm role (table role_requests, V50, SCRUM-204). The
 * applicant and the requested details are fixed at creation; only the decision columns change afterwards.
 */
@Entity
@Table(name = "role_requests")
@Getter
@Setter
public class RoleRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Realm role name, lowercase: investor or lender. */
    @Column(name = "requested_role", nullable = false, updatable = false, length = 20)
    private String requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleRequestStatus status = RoleRequestStatus.PENDING;

    @Column(name = "organization_name", updatable = false, length = 160)
    private String organizationName;

    @Column(updatable = false, length = 1000)
    private String note;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            // PostgreSQL keeps microseconds; truncating makes the response equal to what a later GET returns
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
