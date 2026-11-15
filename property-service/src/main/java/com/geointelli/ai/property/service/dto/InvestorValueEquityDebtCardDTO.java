package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Value, equity and debt card of the investor dashboard (SCRUM-85 / SCRUM-103). Adds up the portfolio value
 * summaries (SCRUM-74) of the current user's portfolios, or of one of them; no other equity formula is used.
 */
@Data
public class InvestorValueEquityDebtCardDTO {

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

    @Schema(description = "Date values and loan balances are taken for (UTC)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate asOfDate;

    @Schema(description = "Currency of every amount", example = "USD")
    private String currency;

    @Schema(description = "Number of holdings in the included portfolios", example = "4")
    private long totalHoldings;

    @Schema(description = "Market value by ownership share")
    private PortfolioMetricDTO value;

    @Schema(description = "Reported debt by ownership share")
    private PortfolioMetricDTO debt;

    @Schema(description = "Equity = value - debt, only for holdings where both are known")
    private PortfolioMetricDTO equity;

    @Schema(description = "Holdings with a known value / totalHoldings x 100; null without holdings", example = "75.00")
    private BigDecimal valueCoveragePercent;

    @Schema(description = "Holdings with a known debt / totalHoldings x 100; null without holdings", example = "75.00")
    private BigDecimal debtCoveragePercent;

    @Schema(description = "Holdings with a known equity / totalHoldings x 100; null without holdings", example = "50.00")
    private BigDecimal equityCoveragePercent;

    @Schema(description = "Oldest loan balance date used")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate oldestBalanceDate;

    @Schema(description = "Newest loan balance date used")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate newestBalanceDate;

    @Schema(description = "Oldest valuation date used")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate oldestValuationDate;

    @Schema(description = "Newest valuation date used")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate newestValuationDate;

    @Schema(description = "Latest change of an included holding")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime holdingsUpdatedAt;

    @Schema(description = "Valuation sources used, sorted")
    private List<String> valuationSources;

    @Schema(description = "SUPPORTED_VALUATION_SERVICE when at least one value is known, "
            + "otherwise UNAVAILABLE_PENDING_SCRUM_58", example = "SUPPORTED_VALUATION_SERVICE")
    private String valuationSource;

    @Schema(description = "Where the debt comes from", example = "OWNER_REPORTED_FINANCING")
    private String debtSource;

    @Schema(description = "How the figures are built")
    private String note;
}
