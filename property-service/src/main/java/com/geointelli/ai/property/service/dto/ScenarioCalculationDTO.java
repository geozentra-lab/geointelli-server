package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of recalculating a saved scenario (SCRUM-148). The calculation uses only the saved assumptions; the
 * current facts of the linked property are shown separately for comparison and never replace a saved value.
 * Nothing is stored.
 */
public record ScenarioCalculationDTO(
        @Schema(description = "Scenario recalculated", example = "5")
        Long scenarioId,
        @Schema(description = "Name chosen by the user", example = "Brickell condo - rent 2500")
        String scenarioName,
        @Schema(description = "Scenario type", example = "INVESTMENT")
        ScenarioType scenarioType,
        @Schema(description = "Version of the assumptions format", example = "1")
        int inputVersion,
        @Schema(description = "User-supplied assumptions, as stored; the only inputs of the calculation")
        JsonNode savedAssumptions,
        @Schema(description = "Current facts of the linked property, for comparison only")
        CurrentFacts currentFacts,
        @Schema(description = "Saved inputs that differ from the current facts or cannot be compared; empty when none")
        List<InputChange> inputChanges,
        @Schema(description = "Output of the shared investment calculation (POST /api/calculations/investment)")
        InvestmentAnalysisResponse result,
        @Schema(description = "How the result was produced")
        String note
) {
    public static final String FACTS_AVAILABLE = "AVAILABLE";
    public static final String NO_LINKED_PROPERTY = "NO_LINKED_PROPERTY";
    public static final String PROPERTY_NOT_FOUND = "PROPERTY_NOT_FOUND";
    public static final String VALUATION_UNAVAILABLE = "VALUATION_UNAVAILABLE";

    public static final String NOTE = "Calculated now from the saved assumptions with the shared investment "
            + "calculation; the result is not stored.";

    /** Current stored valuation of the linked property; value fields are null unless status is AVAILABLE. */
    public record CurrentFacts(
            @Schema(description = "Linked property, or null", example = "100") Long propertyId,
            @Schema(description = "AVAILABLE, NO_LINKED_PROPERTY, PROPERTY_NOT_FOUND or VALUATION_UNAVAILABLE",
                    example = FACTS_AVAILABLE) String status,
            @Schema(description = "Current estimated value in USD", example = "430000.00") BigDecimal estimatedValue,
            @Schema(description = "Model status", example = "supported") String valuationStatus,
            @Schema(description = "Model confidence", example = "high") String confidence,
            @Schema(description = "Prediction date of the valuation", example = "2026-11-01")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate valuationDate) {}

    /** A saved input that differs from, or cannot be compared with, the current facts. */
    public record InputChange(
            @Schema(description = "Saved input concerned", example = "financing.purchasePrice") String field,
            @Schema(description = "Value in the saved assumptions", example = "400000") BigDecimal savedValue,
            @Schema(description = "Current value, or null when unavailable", example = "430000.00")
            BigDecimal currentValue,
            @Schema(description = "What changed or is unavailable") String message) {}
}
