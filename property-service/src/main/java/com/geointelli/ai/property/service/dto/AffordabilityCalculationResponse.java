package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record AffordabilityCalculationResponse(
        BigDecimal monthlyGrossIncome,
        BigDecimal maxMonthlyHousingPayment,
        BigDecimal maxMonthlyPrincipalAndInterest,
        BigDecimal maxLoanAmount,
        BigDecimal maxHomePrice,
        AffordabilityAssumptions assumptions,
        List<String> notes,
        String disclaimer
) {
}
