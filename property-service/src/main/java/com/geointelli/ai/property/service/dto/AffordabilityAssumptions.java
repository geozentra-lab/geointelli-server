package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

public record AffordabilityAssumptions(
        BigDecimal frontEndRatioPercent,
        BigDecimal backEndRatioPercent,
        BigDecimal annualInterestRate,
        Integer loanTermYears,
        String rateSource
) {
}
