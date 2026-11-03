package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Financed metrics, kept separate from the unfinanced operating result (NOI).
 * Cap rate = annual NOI / purchase price; cash-on-cash = annual cash flow / cash invested
 * (down payment + closing costs). A metric is null when its inputs or denominator are missing.
 */
public record InvestmentFinancingResult(
        BigDecimal loanAmount,
        BigDecimal monthlyDebtService,
        BigDecimal annualDebtService,
        BigDecimal monthlyCashFlow,
        BigDecimal annualCashFlow,
        BigDecimal capRatePercent,
        BigDecimal cashInvested,
        BigDecimal cashOnCashPercent,
        List<String> notes
) {
}
