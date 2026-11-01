package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AffordabilityCalculationRequest(

        @NotNull @Positive
        BigDecimal annualGrossIncome,

        @NotNull @PositiveOrZero
        BigDecimal monthlyDebtPayments,

        @NotNull @PositiveOrZero
        BigDecimal downPayment,

        @NotNull @PositiveOrZero @DecimalMax("100")
        BigDecimal annualInterestRate,

        @NotNull @Min(1) @Max(50)
        Integer loanTermYears,

        @PositiveOrZero
        BigDecimal annualPropertyTax,

        @PositiveOrZero
        BigDecimal annualHomeInsurance,

        @PositiveOrZero
        BigDecimal monthlyHoaFees
) {
}
