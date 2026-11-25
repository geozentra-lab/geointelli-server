package com.geointelli.ai.property.service.entity;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One rent observation (SCRUM-132): what a provider reports for one geography, one period and one rent type,
 * either advertised (ASKING) or paid (ACHIEVED) rent. Rows describe a market, never one property or one lease.
 * Every amount is per {@link #rentPeriod}, exactly as published; no converted value is stored. The unique
 * constraint on source, type, geography, period, rent period and bedrooms makes repeated imports update the
 * same row instead of adding a copy.
 */
@Entity
@Table(name = "rent_evidence_observations", uniqueConstraints = @UniqueConstraint(
    name = "uq_rent_evidence_observation",
    columnNames = {"source", "evidence_type", "geography_level", "geography_code", "period_start", "period_end",
                   "rent_period", "bedrooms"}))
@Getter
@Setter
public class RentEvidenceObservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 16)
    private RentEvidenceType evidenceType;

    /** Provider code, for example {@code US_CENSUS_ACS}. */
    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "source_dataset", length = 128)
    private String sourceDataset;

    @Column(name = "source_release_date")
    private LocalDate sourceReleaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "geography_level", nullable = false, length = 16)
    private RentGeographyLevel geographyLevel;

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
    private RentObservationKind observationKind = RentObservationKind.ACTUAL;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    /** Unit of every amount on this row. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rent_period", nullable = false, length = 8)
    private RentPeriod rentPeriod;

    /** Bedrooms the figures refer to; null means all sizes together. */
    @Column
    private Short bedrooms;

    @Column(name = "median_rent", precision = 12, scale = 2)
    private BigDecimal medianRent;

    @Column(name = "average_rent", precision = 12, scale = 2)
    private BigDecimal averageRent;

    @Column(name = "median_rent_per_sqft", precision = 10, scale = 4)
    private BigDecimal medianRentPerSqft;

    /** Provider's margin of error on the median, same unit; null when not published. */
    @Column(name = "median_rent_moe", precision = 12, scale = 2)
    private BigDecimal medianRentMoe;

    /** Listings, leases or survey units behind the figures; null when the provider does not say. */
    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "import_job_id", length = 36)
    private String importJobId;
}
