package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.time.LocalDate;
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

/** Owner-scoped manual deal record linked to a client and property. */
@Entity
@Table(name = "crm_transactions")
@Getter
@Setter
public class CrmTransaction extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, updatable = false) private Long clientId;
    @Column(updatable = false) private Long leadId;
    @Column(nullable = false, updatable = false) private Long propertyId;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CrmTransactionStage stage = CrmTransactionStage.PROSPECT;
    private LocalDate expectedCloseDate;
    private Instant closedAt;
}
