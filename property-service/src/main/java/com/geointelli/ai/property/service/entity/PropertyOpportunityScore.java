package com.geointelli.ai.property.service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Stored output of the opportunity scoring service (SCRUM-76), one row per property with a valuation. Written only
 * by the refresh job through {@code OpportunityScoreJdbcRepository}; read-only here.
 */
@Entity
@Immutable
@Table(name = "property_opportunity_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PropertyOpportunityScore {

    @Id
    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "score_version", nullable = false, length = 32)
    private String scoreVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "coverage_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode factors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode reasons;

    @Column(name = "county_id")
    private Long countyId;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "estimated_value", precision = 38, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "forecast_growth_1y_pct", precision = 9, scale = 2)
    private BigDecimal forecastGrowth1yPct;

    @Column(name = "valuation_run_id", length = 32)
    private String valuationRunId;

    @Column(name = "valuation_model_version", length = 32)
    private String valuationModelVersion;

    @Column(name = "valuation_data_through")
    private LocalDate valuationDataThrough;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;
}
