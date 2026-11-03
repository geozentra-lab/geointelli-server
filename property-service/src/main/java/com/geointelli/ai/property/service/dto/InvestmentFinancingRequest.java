package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Financing inputs of an investment property. Every field is optional: a missing input makes
 * the metrics that need it unavailable (null with a note), it is never assumed. Supplied values
 * are validated by the service; {@code @Digits} bounds their size and decimal places.
 */
public record InvestmentFinancingRequest(
        @Positive @Digits(integer = 12, fraction = 2)
        BigDecimal purchasePrice,

        @PositiveOrZero @Digits(integer = 12, fraction = 2)
        BigDecimal downPayment,

        @PositiveOrZero @DecimalMax("100") @Digits(integer = 3, fraction = 4)
        BigDecimal annualInterestRate,

        @Min(1) @Max(50)
        Integer loanTermYears,

        @PositiveOrZero @Digits(integer = 12, fraction = 2)
        BigDecimal closingCosts
) {
}
