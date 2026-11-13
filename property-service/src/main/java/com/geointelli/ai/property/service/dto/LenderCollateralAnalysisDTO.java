package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Collateral value and loan-to-value of one lender application (SCRUM-83 / SCRUM-180). The collateral value is the
 * stored model valuation of the linked property (SCRUM-58), returned unchanged with its date and confidence.
 * This is informational analysis only: it is not a credit decision and never approves or rejects an application.
 */
public record LenderCollateralAnalysisDTO(
        @Schema(description = "Application analysed", example = "15")
        Long applicationId,
        @Schema(description = "Organization the application belongs to", example = "42")
        Long organizationId,
        @Schema(description = "Linked collateral property; null when the application has none", example = "100")
        Long propertyId,
        @Schema(description = "Currency of every amount", example = "USD")
        String currency,
        @Schema(description = "Loan amount used for the LTV", example = "300000.00")
        BigDecimal loanAmount,
        @Schema(description = "Which amount was used: FUNDED if recorded, else APPROVED, else REQUESTED",
                example = BASIS_APPROVED)
        String loanAmountBasis,
        @Schema(description = "Stored model valuation of the property; null when no property is linked or found")
        Collateral collateral,
        @Schema(description = "Loan-to-value, or why it is unavailable")
        Ltv ltv,
        @Schema(description = "Reminder that this is not a credit decision")
        String disclaimer
) {
    public static final String BASIS_FUNDED = "FUNDED";
    public static final String BASIS_APPROVED = "APPROVED";
    public static final String BASIS_REQUESTED = "REQUESTED";

    public static final String LTV_AVAILABLE = "AVAILABLE";
    public static final String LTV_LIMITED = "LIMITED";
    public static final String LTV_UNAVAILABLE = "UNAVAILABLE";

    public static final String NO_COLLATERAL_PROPERTY = "NO_COLLATERAL_PROPERTY";
    public static final String COLLATERAL_PROPERTY_NOT_FOUND = "COLLATERAL_PROPERTY_NOT_FOUND";
    public static final String VALUATION_UNAVAILABLE = "VALUATION_UNAVAILABLE";
    public static final String INVALID_VALUATION = "INVALID_VALUATION";

    public static final String DISCLAIMER = "Informational only; not a credit decision or approval.";

    /** Stored model valuation of the collateral property, unchanged; value fields are null when unsupported. */
    public record Collateral(
            @Schema(description = "Model status", allowableValues = {"supported", "limited", "unsupported"})
            String valuationStatus,
            @Schema(description = "Estimated value in USD", example = "400000.00") BigDecimal value,
            @Schema(description = "80% range of the estimate") ValuationResponse.Range range80,
            @Schema(description = "90% range of the estimate") ValuationResponse.Range range90,
            @Schema(description = "Model confidence", example = "high") String confidence,
            @Schema(description = "Prediction date of the valuation", example = "2026-11-01")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate valuationDate,
            @Schema(description = "Model version", example = "v3") String modelVersion,
            @Schema(description = "Model caveats, e.g. few comparables") List<ValuationResponse.Reason> reasons) {}

    /** Loan-to-value in percent with its reliability; percent is null when unavailable, never zero or a guess. */
    public record Ltv(
            @Schema(description = "AVAILABLE, LIMITED (computed from a limited valuation) or UNAVAILABLE",
                    example = LTV_AVAILABLE)
            String status,
            @Schema(description = "loanAmount / collateral value x 100, two decimals; null when unavailable",
                    example = "75.00")
            BigDecimal percent,
            @Schema(description = "Why the LTV is limited or unavailable; empty when AVAILABLE")
            List<String> reasons) {}
}
