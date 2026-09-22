package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ExtraFeatureDTO {
    private Integer actualYearBuilt;

    private Double adjustedUnitPrice;

    private BigDecimal depreciatedValue;

    private String description;

    private String message;

    private Double percentCondition;

    private Integer rollYear;

    private Integer units;

    private String useCode;
}
