package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class AssessmentDTO {

    private Long id;

    private BigDecimal assessedValue;

    private BigDecimal buildingOnlyValue;

    private BigDecimal extraFeatureValue;

    private BigDecimal landValue;

    private BigDecimal totalValue;

    private Integer year;

    private String message;
}
