package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * One market observation (SCRUM-131): what a provider reports for one geography and one period, either closed
 * sales or homes offered for sale. Rows describe a market, never one property. The unique constraint on source,
 * type, geography and period makes repeated imports update the same row instead of adding a copy.
 */
@Entity
@Table(name = "market_evidence_observations", uniqueConstraints = @UniqueConstraint(
    name = "uq_market_evidence_observation",
    columnNames = {"source", "evidence_type", "geography_level", "geography_code", "period_start", "period_end"}))
@Getter
@Setter
public class MarketEvidenceObservation extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 16)
    private MarketEvidenceType evidenceType;

    /** Provider code, for example {@code REALTOR_COM_RESEARCH}. */
    @Column(nullable = false, length = 64)
    private String source;
    @Column(name = "source_dataset", length = 128)
    private String sourceDataset;
    @Column(name = "source_release_date")
    private LocalDate sourceReleaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "geography_level", nullable = false, length = 16)
    private MarketGeographyLevel geographyLevel;
    @Column(name = "geography_code", nullable = false, length = 32)
    private String geographyCode;
    @Column(name = "geography_name", length = 120)
    private String geographyName;

    /** Our state and county when the provider's geography matched one of ours; null otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id")
    private County county;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "observation_kind", nullable = false, length = 16)
    private MarketObservationKind observationKind = MarketObservationKind.ACTUAL;
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    // SALE figures: set only when evidenceType is SALE (enforced by the table's check constraint)
    @Column(name = "sale_count")
    private Integer saleCount;
    @Column(name = "median_sale_price", precision = 14, scale = 2)
    private BigDecimal medianSalePrice;
    @Column(name = "average_sale_price", precision = 14, scale = 2)
    private BigDecimal averageSalePrice;
    @Column(name = "median_sale_price_per_sqft", precision = 10, scale = 2)
    private BigDecimal medianSalePricePerSqft;

    // ASKING figures: set only when evidenceType is ASKING (enforced by the table's check constraint)
    @Column(name = "active_listing_count")
    private Integer activeListingCount;
    @Column(name = "new_listing_count")
    private Integer newListingCount;
    @Column(name = "median_asking_price", precision = 14, scale = 2)
    private BigDecimal medianAskingPrice;
    @Column(name = "average_asking_price", precision = 14, scale = 2)
    private BigDecimal averageAskingPrice;
    @Column(name = "median_asking_price_per_sqft", precision = 10, scale = 2)
    private BigDecimal medianAskingPricePerSqft;
    @Column(name = "median_days_on_market", precision = 7, scale = 1)
    private BigDecimal medianDaysOnMarket;
    @Column(name = "price_reduced_count")
    private Integer priceReducedCount;

    /** When the row was last written by an import, and the ingestion job that wrote it. */
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
    @Column(name = "import_job_id", length = 36)
    private String importJobId;
}
