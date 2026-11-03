package com.geointelli.ai.property.service.entity;

import java.time.Instant;
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

/** Append-only history. Historical subject IDs survive client deletion. */
@Entity
@Table(name = "crm_activities")
@Getter
@Setter
public class CrmActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, updatable = false)
    private Long clientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CrmSubjectType subjectType;
    @Column(nullable = false, updatable = false)
    private Long subjectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private CrmActivityType activityType;
    @Column(nullable = false, updatable = false, length = 2000)
    private String description;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
