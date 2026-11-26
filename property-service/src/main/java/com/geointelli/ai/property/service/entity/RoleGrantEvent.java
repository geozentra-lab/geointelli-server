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
 * One grant or revocation of a Keycloak realm role done by GeoZentra (table role_grant_events, V50, SCRUM-204).
 * Append-only: rows are never updated or deleted. {@code decidedByUserId} is null for automatic decisions
 * (investor self-service, license expiry).
 */
@Entity
@Table(name = "role_grant_events")
@Getter
@Setter
public class RoleGrantEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private RoleGrantAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private RoleGrantSource source;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(updatable = false, length = 500)
    private String reason;

    @Column(name = "decided_by_user_id", updatable = false)
    private Long decidedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
