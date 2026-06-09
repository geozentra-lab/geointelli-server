package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class PropertyValuePredictionDTO {

    private Long propertyId;

    private BigDecimal predictedPrice;
    private BigDecimal predictedPriceLow;
    private BigDecimal predictedPriceHigh;
    private BigDecimal growthPercentage;

    private BigDecimal lastSalePrice;
    private LocalDate lastSaleDate;

    private Integer confidenceScore;
    private LocalDate predictionDate;

    private String modelVersion;
}
