package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Opportunities card of the investor dashboard (SCRUM-85 / SCRUM-106): a bounded list of the best stored
 * opportunity scores, read from the shared opportunity service of SCRUM-76. Each item keeps the ranking
 * explanation (factors) and the evidence coverage of that service; nothing is rescored here. When the shared
 * service has no candidate, the card is EMPTY with the service's own reason and no invented items.
 */
@Data
public class InvestorOpportunitiesCardDTO {

    public static final String SCOPE_ALL_PORTFOLIOS = "ALL_PORTFOLIOS";
    public static final String SCOPE_PORTFOLIO = "PORTFOLIO";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "ALL_PORTFOLIOS (every portfolio of the current user) or PORTFOLIO (the one requested)",
            example = SCOPE_ALL_PORTFOLIOS)
    private String scope;

    @Schema(description = "Requested portfolio; null for ALL_PORTFOLIOS", example = "5")
    private Long portfolioId;

    @Schema(description = "Number of portfolios whose holdings are flagged as alreadyHeld", example = "2")
    private int portfolioCount;

    @Schema(description = "OK with at least one item, EMPTY when the shared service has no candidate (see "
            + "candidateStatus), ERROR when the composed dashboard could not load this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card failed; null unless status is ERROR")
    private String error;

    @Schema(description = "When the card was built (UTC)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant generatedAt;

    @Schema(description = "Scoring version of the listed scores (SCRUM-76)", example = "opportunity-v1")
    private String scoreVersion;

    @Schema(description = "Status of the shared opportunity service: OK, or why the candidate set is empty: "
            + "NO_MATCHES (filters exclude every scored property), NO_ELIGIBLE_OPPORTUNITIES (no property has "
            + "enough evidence), NOT_SCORED (scores not calculated yet)", example = "OK")
    private String candidateStatus;

    @Schema(description = "Filters passed to the shared service; null = not applied", example = "1")
    private Long countyId;

    @Schema(description = "Filters passed to the shared service; null = not applied", example = "1")
    private Long stateId;

    @Schema(description = "Maximum number of items in this card", example = "5")
    private int limit;

    @Schema(description = "Number of scored properties matching the filters, before the limit", example = "416226")
    private long totalCandidates;

    @Schema(description = "Best candidates first (score descending, ties by property ID); empty when EMPTY")
    private List<Item> items;

    @Schema(description = "How to read the scores, from the shared service")
    private List<String> notes;

    @Schema(description = "How the card is built")
    private String note;

    /**
     * One listed opportunity: the stored score of SCRUM-76 with its explanation and coverage, plus whether the
     * investor already holds the property.
     */
    public record Item(
            @Schema(description = "Property ID (details: GET /api/properties/id/{id}, live score: "
                    + ".../opportunity-score)", example = "42")
            Long propertyId,
            @Schema(description = "Folio number", example = "3049340040210")
            String folio,
            @Schema(description = "Street address, or null")
            String address,
            @Schema(description = "City, or null", example = "Miami")
            String city,
            @Schema(description = "ZIP code, or null", example = "33183")
            String zip,
            @Schema(description = "County, or null", example = "Miami-Dade")
            String countyName,
            @Schema(description = "State code, or null", example = "FL")
            String stateCode,
            @Schema(description = "Stored score out of maxScore", example = "44.49")
            BigDecimal score,
            @Schema(description = "Highest possible score", example = "100")
            int maxScore,
            @Schema(description = "Share of the evidence that was available; the score never exceeds it",
                    example = "50.00")
            BigDecimal coveragePercent,
            @Schema(description = "ZentraValue estimate the score used (USD)", example = "525636.50")
            BigDecimal estimatedValue,
            @Schema(description = "One-year forecast growth the score used, in percent", example = "10.32")
            BigDecimal forecastGrowth1yPct,
            @Schema(description = "Every factor with its evidence and points, or the reason it is unavailable")
            List<OpportunityScoreDTO.Factor> factors,
            @Schema(description = "Last date of the valuation's data")
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDate valuationDataThrough,
            @Schema(description = "When the score was stored (UTC)")
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant scoredAt,
            @Schema(description = "True when the property is a holding of one of the included portfolios",
                    example = "false")
            boolean alreadyHeld
    ) {
    }
}
