package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * One row per property, written monthly by geointelli-models (src/pipeline/score_all.py --write).
 * predicted_price_low/high is the 80% range; the _90 columns the 90% range. Forecast ranges are
 * illustrative (current-value plus forecast error). notes holds a JSON array of display notes.
 */
@Entity
@Table(name = "property_value_predictions")
@Data
public class PropertyValuePrediction extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "predicted_price", precision = 38, scale = 2)
    private BigDecimal predictedPrice;

    @Column(name = "predicted_price_low", precision = 38, scale = 2)
    private BigDecimal predictedPriceLow;

    @Column(name = "predicted_price_high", precision = 38, scale = 2)
    private BigDecimal predictedPriceHigh;

    @Column(name = "predicted_price_low_90", precision = 38, scale = 2)
    private BigDecimal predictedPriceLow90;

    @Column(name = "predicted_price_high_90", precision = 38, scale = 2)
    private BigDecimal predictedPriceHigh90;

    /** high / medium / low */
    @Column(name = "confidence", length = 10)
    private String confidence;

    @Column(name = "has_sale_history")
    private Boolean hasSaleHistory;

    @Column(name = "last_sale_price", precision = 38, scale = 2)
    private BigDecimal lastSalePrice;

    @Column(name = "last_sale_date")
    private LocalDate lastSaleDate;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "forecast_1y", precision = 38, scale = 2)
    private BigDecimal forecast1y;

    @Column(name = "forecast_1y_low", precision = 38, scale = 2)
    private BigDecimal forecast1yLow;

    @Column(name = "forecast_1y_high", precision = 38, scale = 2)
    private BigDecimal forecast1yHigh;

    /** 1-year value from the market index alone, without the property adjustment. */
    @Column(name = "unchanged_home_value_1y", precision = 38, scale = 2)
    private BigDecimal unchangedHomeValue1y;

    @Column(name = "forecast_3y", precision = 38, scale = 2)
    private BigDecimal forecast3y;

    @Column(name = "forecast_3y_low", precision = 38, scale = 2)
    private BigDecimal forecast3yLow;

    @Column(name = "forecast_3y_high", precision = 38, scale = 2)
    private BigDecimal forecast3yHigh;

    @Column(name = "forecast_7y", precision = 38, scale = 2)
    private BigDecimal forecast7y;

    @Column(name = "forecast_7y_low", precision = 38, scale = 2)
    private BigDecimal forecast7yLow;

    @Column(name = "forecast_7y_high", precision = 38, scale = 2)
    private BigDecimal forecast7yHigh;

    @Column(name = "market_outlook_1y_pct")
    private Double marketOutlook1yPct;

    @Column(name = "prediction_date")
    private LocalDate predictionDate;

    /** Last sale date in the training data. */
    @Column(name = "data_through")
    private LocalDate dataThrough;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    /** JSON array of strings. */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** supported / limited / unsupported; the value must not be shown when unsupported. */
    @Column(name = "valuation_status", length = 12)
    private String valuationStatus;

    /** JSON array of reason codes, e.g. ["low_confidence","no_sale_history"]. */
    @Column(name = "status_reasons", columnDefinition = "text")
    private String statusReasons;

    @Column(name = "valuation_method", length = 64)
    private String valuationMethod;

    /**
     * JSON: {"baseline": typical property value today, "factors": [{"f": code, "pct": effect %, "usd": effect $}]},
     * largest factors first, then "other". Codes: living_area, rooms, lot_and_land, age_and_condition,
     * county_assessment, location, nearby_sales, sale_history, property_type, market_timing.
     */
    @Column(name = "valuation_factors", columnDefinition = "text")
    private String valuationFactors;

    /** Scoring run; its input snapshot and artifact hashes are kept by geointelli-models. */
    @Column(name = "run_id", length = 32)
    private String runId;

    /** Hash of this property's model inputs in that run. */
    @Column(name = "input_hash", length = 16)
    private String inputHash;
}
