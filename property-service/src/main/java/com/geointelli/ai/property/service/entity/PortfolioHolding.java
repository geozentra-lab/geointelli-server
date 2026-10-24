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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** User-reported acquisition, financing and occupancy facts for an internal property. */
@Entity
@Table(name = "portfolio_holdings", uniqueConstraints = @UniqueConstraint(
    name = "uq_portfolio_holdings_property", columnNames = {"portfolio_id", "property_id"}))
@Getter
@Setter
public class PortfolioHolding extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    private LocalDate acquisitionDate;
    @Column(precision = 14, scale = 2)
    private BigDecimal purchasePrice;
    @Column(precision = 14, scale = 2)
    private BigDecimal acquisitionCosts;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ownershipSharePercent = new BigDecimal("100");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldingFinancingType financingType = HoldingFinancingType.UNKNOWN;
    @Column(precision = 14, scale = 2)
    private BigDecimal loanAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal outstandingBalance;
    private LocalDate balanceAsOfDate;
    @Column(precision = 7, scale = 4)
    private BigDecimal interestRatePercent;
    private Integer loanTermMonths;
    @Column(precision = 14, scale = 2)
    private BigDecimal monthlyPayment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldingOccupancyStatus occupancyStatus = HoldingOccupancyStatus.UNKNOWN;
    @Column(precision = 14, scale = 2)
    private BigDecimal monthlyRent;
    private LocalDate leaseStartDate;
    private LocalDate leaseEndDate;
}
