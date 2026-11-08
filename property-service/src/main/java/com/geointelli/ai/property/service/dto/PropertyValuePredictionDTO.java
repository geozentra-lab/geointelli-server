package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** Change from the last sale price to predictedPrice, in percent (computed by the service). */
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
}
