package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Affordability card of the user dashboard (SCRUM-99). Reuses the affordability calculator
 * (SCRUM-63) on the inputs supplied by the caller; no salary, debt, rate or HOA value is
 * ever assumed, so missing required inputs give an INCOMPLETE card instead of an estimate.
 */
@Data
public class AffordabilityCardDTO {

    public static final String SCOPE_CURRENT_USER = "CURRENT_USER";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "Whose data the card shows; always CURRENT_USER", example = SCOPE_CURRENT_USER)
    private String scope;

    @Schema(description = "Freshness: when the card was built (server time)")
    private LocalDateTime generatedAt;

    @Schema(description = "OK when every required input was supplied; INCOMPLETE when some were never supplied; "
            + "ERROR when the dashboard could not load this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card has no estimate (INCOMPLETE or ERROR); null when status is OK",
            example = "Required financial inputs not supplied")
    private String error;

    @Schema(description = "Names of the required inputs that were not supplied; empty when status is OK",
            example = "[\"annualGrossIncome\", \"monthlyDebtPayments\"]")
    private List<String> missingInputs;

    @Schema(description = "Affordability calculation results if status is OK; null otherwise")
    private AffordabilityCalculationResponse affordability;
}
