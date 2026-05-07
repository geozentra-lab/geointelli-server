package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BuildingDTO {

    private Long id;

    private Integer actual;

    private Integer actualArea;

    private Double adjustedBasePrice;

    private Integer buildingNo;

    private BigDecimal depreciatedValue;

    private Integer effective;

    private Integer effectiveArea;

    private Integer grossArea;

    private Integer heatedArea;

    private String message;

    private Double percentComp;

    private Double percentageGood;

    private BigDecimal replacementCostNew;

    private Integer rollYear;

    private Integer segNo;

    private Integer totalAdjustedPoints;

    private String traversePoints;

    private String improvementModelDesc;
}

