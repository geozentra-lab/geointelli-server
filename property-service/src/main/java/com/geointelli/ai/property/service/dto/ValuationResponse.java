package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Stored model output for display; absent sections are omitted when valuation is unsupported. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValuationResponse(
        @Schema(allowableValues = {"supported", "limited", "unsupported"}) String status,
        List<Reason> reasons, Estimate estimate, Forecasts forecasts, Explanation explanation,
        Evidence evidence, Method method, Reproducibility reproducibility, List<String> notes) {

    /** Contract reason code and its readable caveat. */
    public record Reason(String code, String message) {}

    /** Model values in USD, unchanged from storage; ranges have 80% and 90% coverage. */
    public record Estimate(BigDecimal value, Range range80, Range range90, String confidence) {}

    /** Model lower and upper bounds in USD. */
    public record Range(BigDecimal low, BigDecimal high) {}

    /** Model forecasts; three/seven-year values are trend scenarios and all forecast ranges are illustrative. */
    public record Forecasts(Projection oneYear, Projection threeYears, Projection sevenYears) {}

    /** A stored projected value and its illustrative bounds, in USD. */
    public record Projection(BigDecimal value, BigDecimal low, BigDecimal high) {}

    /** Model explanation; factor amounts overlap and must never be summed. */
    public record Explanation(BigDecimal baseline, String summary, List<Factor> factors, String note) {}

    /** Original percentage and USD effects, with rounded percentages used only in the display text. */
    public record Factor(String code, String label, BigDecimal pct, BigDecimal usd, String text) {}

    /** Historical sale and separately selected supporting comparables, not model calculation inputs. */
    public record Evidence(LastSale lastSale, SupportingComparables comparables) {}

    /** Last qualified sale as recorded by the model pipeline. */
    public record LastSale(Boolean hasSaleHistory, BigDecimal price, LocalDate date) {}

    /** Current comparables retain their own selection dates, warnings, scoring and availability status. */
    public record SupportingComparables(String label, String status, ComparableResponse data) {}

    /** Stored model provenance; dataThrough is the last sale date in model data, not a live market timestamp. */
    public record Method(String valuationMethod, String modelVersion, LocalDate predictionDate,
            LocalDate dataThrough, LocalDateTime createdAt) {}

    /** References to the retained Python scoring run and the property's exact model inputs. */
    public record Reproducibility(String runId, String inputHash) {}
}
