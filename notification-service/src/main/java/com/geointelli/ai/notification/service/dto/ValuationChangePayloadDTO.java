package com.geointelli.ai.notification.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Frozen estimate versions; percentage always uses the old estimate as
 * denominator.
 */
public record ValuationChangePayloadDTO(@NotBlank @Size(max = 120) String ruleName,
        @NotNull @Pattern(regexp = "USD") String currency,
        @NotNull @Valid Snapshot oldValuation, @NotNull @Valid Snapshot newValuation,
        @NotNull BigDecimal changeAmount, @NotNull BigDecimal changePercent,
        @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal minimumChangePercent,
        @NotNull @Pattern(regexp = "ANY|INCREASE|DECREASE") String configuredDirection,
        @NotNull @Pattern(regexp = "INCREASE|DECREASE") String direction,
        @NotNull Instant observationStartedAt, @NotNull Instant observationFinishedAt) {
    public record Snapshot(@Positive long historyId, @Size(max = 32) String runId, @Size(max = 32) String modelVersion,
            LocalDate predictionDate, @NotNull @Pattern(regexp = "supported|limited") String status,
            @NotNull @Positive BigDecimal value, @NotNull Instant loadedAt) {
    }
}
