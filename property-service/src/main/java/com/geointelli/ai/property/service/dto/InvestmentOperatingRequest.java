package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record InvestmentOperatingRequest(

        @NotNull @PositiveOrZero
        BigDecimal monthlyRent,

        @PositiveOrZero @DecimalMax("100")
        BigDecimal vacancyRatePercent,

        @PositiveOrZero @DecimalMax("100")
        BigDecimal maintenanceRatePercent,

        @PositiveOrZero @DecimalMax("100")
        BigDecimal managementRatePercent,

        @PositiveOrZero
        BigDecimal annualPropertyTax,

        @PositiveOrZero
        BigDecimal annualHomeInsurance,

        @PositiveOrZero
        BigDecimal monthlyHoaFees,

        @PositiveOrZero
        BigDecimal monthlyOtherExpenses
) {
}
