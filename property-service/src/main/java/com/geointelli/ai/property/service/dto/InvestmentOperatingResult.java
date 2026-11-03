package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentOperatingResult(
        BigDecimal monthlyGrossRent,
        BigDecimal annualGrossRent,
        BigDecimal monthlyVacancyLoss,
        BigDecimal annualVacancyLoss,
        BigDecimal monthlyEffectiveIncome,
        BigDecimal annualEffectiveIncome,
        BigDecimal annualMaintenance,
        BigDecimal annualManagement,
        BigDecimal annualPropertyTax,
        BigDecimal annualInsurance,
        BigDecimal annualHoaFees,
        BigDecimal annualOtherExpenses,
        BigDecimal monthlyOperatingExpenses,
        BigDecimal annualOperatingExpenses,
        BigDecimal monthlyNetOperatingIncome,
        BigDecimal annualNetOperatingIncome,
        List<String> notes
) {
}
