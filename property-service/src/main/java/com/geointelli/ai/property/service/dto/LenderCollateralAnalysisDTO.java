package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Collateral value, loan-to-value and geographic risk context of one lender application (SCRUM-83 / SCRUM-180 /
 * SCRUM-181). The collateral value is the stored model valuation of the linked property (SCRUM-58), returned
 * unchanged with its date and confidence; the risk context is the property risk section of SCRUM-127, also
 * unchanged. Each section has its own status, so a missing one never hides the others. This is informational
 * analysis only: no risk score is computed, and it is not a credit decision.
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
        @Schema(description = "Location of the collateral and its geographic risk evidence, with the sources and "
                + "the missing coverage")
        Geography geography,
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

    /** Every risk source has evidence for the location. */
    public static final String GEOGRAPHY_AVAILABLE = "AVAILABLE";
    /** Some risk sources have evidence; the others are listed in missingCoverage. */
    public static final String GEOGRAPHY_PARTIAL = "PARTIAL";
    /** No risk source covers the location (no parcel polygon, or no source loaded for it). */
    public static final String GEOGRAPHY_NO_COVERAGE = "NO_COVERAGE";
    /** The risk context could not be built: see reasons. */
    public static final String GEOGRAPHY_UNAVAILABLE = "UNAVAILABLE";

    public static final String RISK_CONTEXT_UNAVAILABLE = "RISK_CONTEXT_UNAVAILABLE";
    public static final String NO_PARCEL_POLYGON = "NO_PARCEL_POLYGON";

    public static final String SECTION_FLOOD = "FLOOD";
    public static final String SECTION_HAZARDS = "HAZARDS";
    public static final String SECTION_STORMS = "STORMS";

    /** The SCRUM-180 response, before the geography section; the geography is then null. */
    public LenderCollateralAnalysisDTO(Long applicationId, Long organizationId, Long propertyId, String currency,
                                       BigDecimal loanAmount, String loanAmountBasis, Collateral collateral, Ltv ltv,
                                       String disclaimer) {
        this(applicationId, organizationId, propertyId, currency, loanAmount, loanAmountBasis, collateral, ltv, null,
                disclaimer);
    }

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

    /**
     * Where the collateral is and what the risk sources say about that place. No GeoZentra score: the risk context
     * holds the FEMA and NOAA values as published, and a source without evidence is listed, never read as low risk.
     */
    public record Geography(
            @Schema(description = "AVAILABLE, PARTIAL (some sources missing), NO_COVERAGE (no source covers the "
                    + "location) or UNAVAILABLE (see reasons)", example = GEOGRAPHY_PARTIAL)
            String status,
            @Schema(description = "Why the geography is incomplete: NO_COLLATERAL_PROPERTY, "
                    + "COLLATERAL_PROPERTY_NOT_FOUND, NO_PARCEL_POLYGON or RISK_CONTEXT_UNAVAILABLE; empty when "
                    + "AVAILABLE or PARTIAL")
            List<String> reasons,
            @Schema(description = "Address, county and state of the collateral; null when no property is found")
            Location location,
            @Schema(description = "Each risk source with its status and data date, in the order FLOOD, HAZARDS, "
                    + "STORMS; empty when the risk context could not be evaluated")
            List<RiskSource> sources,
            @Schema(description = "Sections whose source does not cover the location or could not be read "
                    + "(status NO_COVERAGE or UNAVAILABLE)", example = "[\"STORMS\"]")
            List<String> missingCoverage,
            @Schema(description = "Property risk section of SCRUM-127, unchanged; null when it could not be read")
            RiskContextDTO riskContext) {

        /** A geography that could not be evaluated, for one reason. */
        public static Geography unavailable(String reason, Location location) {
            return new Geography(GEOGRAPHY_UNAVAILABLE, List.of(reason), location, List.of(), List.of(), null);
        }
    }

    /** Address of the collateral property as stored. */
    public record Location(
            @Schema(description = "County parcel number", example = "0141340001010") String folio,
            @Schema(description = "Street address", example = "1234 NW 5 AVE") String address,
            @Schema(description = "City", example = "MIAMI") String city,
            @Schema(description = "ZIP code", example = "33136") String zip,
            @Schema(description = "County", example = "Miami-Dade") String county,
            @Schema(description = "State code", example = "FL") String state) {}

    /** One risk source of the location, as reported by the SCRUM-127 risk context. */
    public record RiskSource(
            @Schema(description = "FLOOD, HAZARDS or STORMS", example = SECTION_FLOOD) String section,
            @Schema(description = "Source name", example = RiskContextDTO.SOURCE_FLOOD) String source,
            @Schema(description = "AVAILABLE, NO_DATA (the source has no row for the location; not evidence of low "
                    + "risk), NO_COVERAGE (not loaded for the location) or UNAVAILABLE (could not be read)",
                    example = RiskContextDTO.STATUS_AVAILABLE) String status,
            @Schema(description = "Date the source data was downloaded; null when unknown", example = "2026-11-14")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate dataAsOf,
            @Schema(description = "Explanation from the source section; null when none") String note) {}

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
