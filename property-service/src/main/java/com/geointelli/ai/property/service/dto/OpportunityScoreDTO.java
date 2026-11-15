package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Opportunity score of one property (SCRUM-167), with every factor, the share of the evidence that was
 * available and the valuation it was calculated from. Missing evidence never adds points; when essential
 * evidence is missing the score is unavailable rather than a misleading number.
 */
public record OpportunityScoreDTO(
        @Schema(description = "Property ID", example = "95340")
        Long propertyId,
        @Schema(description = "Version of the documented scoring rules", example = "opportunity-v1")
        String scoreVersion,
        @Schema(description = "SCORED, or UNAVAILABLE when essential evidence is missing (see reasons)",
                example = "SCORED")
        String status,
        @Schema(description = "Points earned out of maxScore; null when status is UNAVAILABLE", example = "31.84")
        BigDecimal score,
        @Schema(description = "Highest possible score", example = "100")
        int maxScore,
        @Schema(description = "Sum of the weights of the factors whose evidence was available; the score can never "
                + "exceed it", example = "50.00")
        BigDecimal coveragePercent,
        @Schema(description = "Every factor of the scoring version, available or not")
        List<Factor> factors,
        @Schema(description = "Why the score is unavailable; empty when status is SCORED")
        List<Reason> reasons,
        @Schema(description = "The stored valuation the score was calculated from; null without a valuation")
        ValuationBasis valuation,
        @Schema(description = "When the score was calculated (UTC)")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant scoredAt,
        @Schema(description = "How to read the score")
        List<String> notes
) {
    /** {@link #status} when the essential evidence exists and the score is set. */
    public static final String STATUS_SCORED = "SCORED";
    /** {@link #status} when essential evidence is missing; the score is null and {@link #reasons} explain why. */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /**
     * One factor of the score.
     *
     * @param code        stable factor code, e.g. FORECAST_GROWTH_1Y
     * @param label       readable name
     * @param category    VALUATION, MARKET, RENT, FINANCING or PRICE
     * @param weight      maximum points the factor can add (all weights add up to 100)
     * @param status      AVAILABLE or UNAVAILABLE
     * @param signal      the measured evidence (e.g. forecast growth in percent); null when unavailable
     * @param signalUnit  unit of {@code signal}: PERCENT or SCORE; null when unavailable
     * @param subscore    the signal on a 0 to 100 scale; null when unavailable
     * @param points      weight × subscore / 100; null when unavailable (it adds no points and is not a zero cost)
     * @param explanation how the factor was scored, or why it is unavailable
     */
    public record Factor(String code, String label, String category, int weight, String status, BigDecimal signal,
                         String signalUnit, BigDecimal subscore, BigDecimal points, String explanation) {
    }

    /** Why the overall score is unavailable. */
    public record Reason(String code, String message) {
    }

    /**
     * The stored ZentraValue valuation the score used (SCRUM-58).
     *
     * @param estimatedValue  current estimate, or null
     * @param valuationStatus supported, limited or unsupported
     * @param confidence      high, medium or low
     * @param modelVersion    valuation model version
     * @param dataThrough     last date of the valuation's input data
     * @param runId           valuation run
     */
    public record ValuationBasis(BigDecimal estimatedValue, String valuationStatus, String confidence,
                                 String modelVersion, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate dataThrough,
                                 String runId) {
    }
}
