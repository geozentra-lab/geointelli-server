package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

public record MortgageCalculationResponse(
        BigDecimal loanAmount,
        BigDecimal monthlyPrincipalAndInterest,
        BigDecimal monthlyPropertyTax,
        BigDecimal monthlyInsurance,
        BigDecimal monthlyHoaFees,
        BigDecimal totalMonthlyKnownCosts
) {
}
