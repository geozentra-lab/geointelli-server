package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Equity and appreciation projection at the end of the horizon, kept separate from the current returns
 * (SCRUM-146). Equity = down payment + loan principal repaid + appreciation. A value whose inputs are missing
 * is null with a note; {@code assumptions} lists every hypothesis the projection relies on.
 */
public record InvestmentProjectionResult(
        Integer horizonYears,
        BigDecimal annualAppreciationRatePercent,
        BigDecimal projectedPropertyValue,
        BigDecimal remainingLoanBalance,
        BigDecimal equityFromDownPayment,
        BigDecimal equityFromLoanPaydown,
        BigDecimal equityFromAppreciation,
        BigDecimal totalEquity,
        BigDecimal cumulativeCashFlow,
        List<String> assumptions,
        List<String> notes
) {
}
