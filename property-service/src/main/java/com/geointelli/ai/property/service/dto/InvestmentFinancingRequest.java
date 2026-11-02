package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Financing inputs of an investment property. Every field is optional: a missing input makes
 * the metrics that need it unavailable (null with a note), it is never assumed.
 */
public record InvestmentFinancingRequest(
        @Positive
        BigDecimal purchasePrice,

        @PositiveOrZero
        BigDecimal downPayment,

        @PositiveOrZero @DecimalMax("100")
        BigDecimal annualInterestRate,

        @Min(1) @Max(50)
        Integer loanTermYears,

        @PositiveOrZero
        BigDecimal closingCosts
) {
}
