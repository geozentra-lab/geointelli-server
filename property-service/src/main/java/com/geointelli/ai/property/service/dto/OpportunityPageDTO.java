package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of {@code GET /api/opportunities} (SCRUM-168): stored opportunity scores, best first by default, each
 * with its factors and coverage. Only SCORED properties are listed; properties whose score is unavailable are
 * never ranked.
 */
public record OpportunityPageDTO(
        @Schema(description = "Scoring version of every listed score", example = "opportunity-v1")
        String scoreVersion,
        @Schema(description = "OK; NO_MATCHES when the filters exclude every scored property; NO_ELIGIBLE_OPPORTUNITIES "
                + "when no property has enough evidence to be scored; NOT_SCORED when the scores have not been "
                + "calculated yet", example = "OK")
        String status,
        @Schema(description = "The filters that were applied (null = not applied)")
        Filters filters,
        @Schema(description = "Applied order", example = "SCORE_DESC")
        String sort,
        @Schema(description = "The requested page")
        PageResponse<Item> results,
        @Schema(description = "How to read the scores")
        List<String> notes
) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_NO_MATCHES = "NO_MATCHES";
    public static final String STATUS_NO_ELIGIBLE = "NO_ELIGIBLE_OPPORTUNITIES";
    public static final String STATUS_NOT_SCORED = "NOT_SCORED";

    /** Applied filters. */
    public record Filters(Long countyId, Long stateId, BigDecimal minScore, BigDecimal minValue, BigDecimal maxValue) {
    }

    /**
     * One scored property.
     *
     * @param propertyId           property ID (details: GET /api/properties/id/{id}, live score: .../opportunity-score)
     * @param folio                folio number
     * @param address              street address, or null
     * @param city                 city, or null
     * @param zip                  ZIP code, or null
     * @param countyName           county, or null
     * @param stateCode            state code, or null
     * @param score                stored score out of maxScore
     * @param maxScore             highest possible score (100)
     * @param coveragePercent      share of the evidence that was available; the score cannot exceed it
     * @param estimatedValue       ZentraValue estimate the score used (USD)
     * @param forecastGrowth1yPct  one-year forecast growth the score used, in percent
     * @param factors              every factor with its evidence or the reason it is unavailable
     * @param valuationRunId       valuation run the score was calculated from
     * @param valuationDataThrough last date of that valuation's data
     * @param scoredAt             when the score was stored (UTC)
     */
    public record Item(Long propertyId, String folio, String address, String city, String zip, String countyName,
                       String stateCode, BigDecimal score, int maxScore, BigDecimal coveragePercent,
                       BigDecimal estimatedValue, BigDecimal forecastGrowth1yPct, List<OpportunityScoreDTO.Factor> factors,
                       String valuationRunId, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate valuationDataThrough,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) Instant scoredAt) {
    }
}
