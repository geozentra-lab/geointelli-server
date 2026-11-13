package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Occupancy and exposure card of the investor dashboard (SCRUM-85 / SCRUM-105). Adds up the portfolio exposure
 * summaries (SCRUM-74) of the current user's portfolios, or of one of them; nothing is recalculated differently.
 */
@Data
public class InvestorOccupancyExposureCardDTO {

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

    @Schema(description = "Number of portfolios included", example = "2")
    private int portfolioCount;

    @Schema(description = "OK with holdings, EMPTY without holdings, ERROR when the composed dashboard could not load "
            + "this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card failed; null unless status is ERROR")
    private String error;

    @Schema(description = "When the card was built (UTC)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant generatedAt;

    @Schema(description = "Date the current holdings are described for (UTC)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate asOfDate;

    @Schema(description = "Number of holdings in the included portfolios", example = "5")
    private long totalHoldings;

    @Schema(description = "Holdings per owner-reported occupancy; every status is present, UNKNOWN included")
    private Map<HoldingOccupancyStatus, Long> occupancyCounts;

    @Schema(description = "Holdings whose occupancy is known (not UNKNOWN)", example = "4")
    private long knownOccupancyHoldings;

    @Schema(description = "knownOccupancyHoldings / totalHoldings × 100; null without holdings", example = "80.00")
    private BigDecimal occupancyCoveragePercent;

    @Schema(description = "Rental holdings: RENTED + VACANT", example = "3")
    private long rentalHoldings;

    @Schema(description = "RENTED / (RENTED + VACANT) × 100; null without rental holdings", example = "66.67")
    private BigDecimal rentalOccupancyPercent;

    @Schema(description = "Holdings whose state is known", example = "4")
    private long knownGeographyHoldings;

    @Schema(description = "knownGeographyHoldings / totalHoldings × 100; null without holdings", example = "80.00")
    private BigDecimal geographyCoveragePercent;

    @Schema(description = "Exposure basis: HOLDING_COUNT (not value-weighted)", example = "HOLDING_COUNT")
    private String exposureBasis;

    @Schema(description = "Holdings per state, unknown state included; empty without holdings")
    private List<PortfolioGeographyDTO> states;

    @Schema(description = "Holdings per county, unknown county included; empty without holdings")
    private List<PortfolioGeographyDTO> counties;

    @Schema(description = "Latest change of an included holding")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime holdingsUpdatedAt;

    @Schema(description = "How the figures are built")
    private String note;
}
