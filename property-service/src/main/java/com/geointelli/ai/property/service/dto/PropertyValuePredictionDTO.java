package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PropertyValuePredictionDTO {

    private Long propertyId;

    /** Current value and its 80% / 90% ranges. */
    private BigDecimal predictedPrice;
    private BigDecimal predictedPriceLow;
    private BigDecimal predictedPriceHigh;
    private BigDecimal predictedPriceLow90;
    private BigDecimal predictedPriceHigh90;
    /** Projected one-year value change: (forecast1y - predictedPrice) / predictedPrice * 100.
     * Null for unsupported valuations or unavailable/nonpositive values; negative change is valid.
     * This is not appreciation since the last sale. The horizon starts at predictionDate.
     */
    @Schema(description = "Projected 1-year value change (%), relative to "
        + "predictedPrice at predictionDate. Null when unsupported/unavailable; can be negative. Not historical growth.")
    private BigDecimal growthPercentage;

    private String confidence;
    private Integer confidenceScore;
    private Boolean hasSaleHistory;

    private BigDecimal lastSalePrice;
    private LocalDate lastSaleDate;

    /** Forecasts; 3/7-year values are trend scenarios and their ranges are illustrative. */
    private BigDecimal forecast1y;
    private BigDecimal forecast1yLow;
    private BigDecimal forecast1yHigh;
    private BigDecimal unchangedHomeValue1y;
    private BigDecimal forecast3y;
    private BigDecimal forecast3yLow;
    private BigDecimal forecast3yHigh;
    private BigDecimal forecast7y;
    private BigDecimal forecast7yLow;
    private BigDecimal forecast7yHigh;
    private Double marketOutlook1yPct;

    private LocalDate predictionDate;
    private LocalDate dataThrough;
    private String modelVersion;
    /** JSON array of display notes. */
    private String notes;

    /** supported / limited / unsupported, with JSON reason codes. */
    private String valuationStatus;
    private String statusReasons;
    private String valuationMethod;
    /** JSON of baseline value and top factors (see PropertyValuePrediction#valuationFactors). */
    private String valuationFactors;
    private String runId;
    private String inputHash;
}
