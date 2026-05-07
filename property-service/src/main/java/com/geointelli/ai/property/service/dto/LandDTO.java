package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class LandDTO {

    private Long id;
    
    private Double adjustedUnitPrice;

    private BigDecimal calculatedValue;

    private Double depth;

    private Double frontFeet;

    private String landUse;

    private String landlineType;

    private String message;

    private String muniZone;

    private String muniZoneDescription;

    private String paZoneDescription;

    private Double percentCondition;

    private Integer rollYear;

    private Integer totalAdjustments;

    private String unitType;

    private Double units;

    private String useCode;

    private String zone;
}
