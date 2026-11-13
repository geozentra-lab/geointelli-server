package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Projection inputs of an investment scenario (SCRUM-146). The horizon is required; the appreciation rate is
 * optional and only ever comes from the user: when it is missing, no market appreciation is projected.
 */
public record InvestmentProjectionRequest(
        @NotNull @Min(1) @Max(30)
        Integer horizonYears,

        @DecimalMin("-50") @DecimalMax("50") @Digits(integer = 2, fraction = 4)
        BigDecimal annualAppreciationRatePercent
) {
}
