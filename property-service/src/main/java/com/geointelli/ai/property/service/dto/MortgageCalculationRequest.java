package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inputs of the mortgage calculator. {@code @Digits} bounds both the size and the decimal places of
 * every amount: inputs such as {@code 1e2000000} or {@code 1e-2000000} are rejected with a 400 before
 * any calculation instead of tying up the server.
 */
public record MortgageCalculationRequest(

        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 2)
        BigDecimal price,

        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 2)
        BigDecimal downPayment,

        @NotNull
        @PositiveOrZero
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 4)
        BigDecimal annualInterestRate,

        @NotNull
        @Min(1)
        @Max(50)
        Integer loanTermYears,

        @PositiveOrZero
        @Digits(integer = 12, fraction = 2)
        BigDecimal annualPropertyTax,

        @PositiveOrZero
        @Digits(integer = 12, fraction = 2)
        BigDecimal annualHomeInsurance,

        @PositiveOrZero
        @Digits(integer = 12, fraction = 2)
        BigDecimal monthlyHoaFees
) {
}
