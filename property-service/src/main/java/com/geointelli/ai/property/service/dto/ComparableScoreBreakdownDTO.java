package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

/** Points awarded by each rule; null means the required evidence is unavailable. */
@Data
public class ComparableScoreBreakdownDTO {
    private BigDecimal location;
    private BigDecimal distance;
    private BigDecimal livingArea;
    private BigDecimal bedrooms;
    private BigDecimal bathrooms;
    private BigDecimal yearBuilt;
    private BigDecimal saleRecency;
}
