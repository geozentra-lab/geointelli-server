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

/** Owned follow-up work. Associations are validated on creation and remain immutable. */
@Entity
@Table(name = "crm_tasks")
@Getter
@Setter
public class CrmTask extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long userId;
    @Column(updatable = false) private Long clientId;
    @Column(updatable = false) private Long leadId;
    @Column(updatable = false) private Long propertyId;
    @Column(updatable = false) private Long transactionId;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrmTaskStatus status = CrmTaskStatus.OPEN;
    private Instant dueAt;
    private Instant completedAt;
}
