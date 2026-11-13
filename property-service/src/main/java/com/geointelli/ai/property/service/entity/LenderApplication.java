package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
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

/** Organization-owned manual or imported application snapshot. No origination is performed. */
@Entity
@Table(name = "lender_applications")
@Getter
@Setter
public class LenderApplication extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long organizationId;
    @Column(nullable = false, updatable = false) private Long createdByUserId;
    @Column(nullable = false, length = 160) private String applicantName;
    @Column(length = 254) private String applicantEmail;
    private Long propertyId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal requestedAmount;
    @Column(nullable = false, length = 3) private String currency = "USD";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private LenderApplicationStage stage = LenderApplicationStage.DRAFT;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private LenderLoanType loanType = LenderLoanType.UNKNOWN;
    @Column(precision = 18, scale = 2) private BigDecimal approvedAmount;
    @Column(precision = 18, scale = 2) private BigDecimal fundedAmount;
    private Instant approvedAt;
    private Instant fundedAt;
    @Column(length = 2000) private String notes;
    @Column(length = 80, updatable = false) private String source;
    @Column(length = 160, updatable = false) private String externalId;
}

