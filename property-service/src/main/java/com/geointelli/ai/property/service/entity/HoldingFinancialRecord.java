package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Actual transactions reported by the owner, separate from official property data and projected scenarios. */
@Entity
@Table(name = "holding_financial_records")
@Getter
@Setter
public class HoldingFinancialRecord extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holding_id", nullable = false)
    private PortfolioHolding holding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinancialRecordCategory category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false)
    private LocalDate transactionDate;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Column(length = 2000)
    private String description;
}
