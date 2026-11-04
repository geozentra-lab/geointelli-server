package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

public record RateAssumptions(
        BigDecimal annualInterestRate,
        Integer loanTermYears,
        String rateSource
) {
}
