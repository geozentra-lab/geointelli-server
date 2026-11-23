package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Composed investor dashboard (SCRUM-85 / SCRUM-107): the four investor cards for the same portfolio scope, in one
 * response. A card that could not be loaded keeps its place with status ERROR; the other cards are kept.
 */
@Data
public class InvestorDashboardDTO {

    public static final String SCOPE_ALL_PORTFOLIOS = "ALL_PORTFOLIOS";
    public static final String SCOPE_PORTFOLIO = "PORTFOLIO";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_ERROR = "ERROR";

    public static final String CARD_VALUE_EQUITY_DEBT = "valueEquityDebt";
    public static final String CARD_INCOME_EXPENSES_RETURNS = "incomeExpensesReturns";
    public static final String CARD_OCCUPANCY_EXPOSURE = "occupancyExposure";
    public static final String CARD_OPPORTUNITIES = "opportunities";

    @Schema(description = "ALL_PORTFOLIOS (every portfolio of the current user) or PORTFOLIO (the one requested)",
            example = SCOPE_ALL_PORTFOLIOS)
    private String scope;

    @Schema(description = "Requested portfolio; null for ALL_PORTFOLIOS", example = "5")
    private Long portfolioId;

    @Schema(description = "Number of portfolios included (at most 100)", example = "2")
    private int portfolioCount;

    @Schema(description = "OK: every card loaded; EMPTY: every card loaded and empty; PARTIAL: some cards failed "
            + "(see failedCards), the others are kept; ERROR: no card could be loaded", example = STATUS_OK)
    private String status;

    @Schema(description = "Names of the cards with status ERROR; empty when every card loaded",
            example = "[\"opportunities\"]")
    private List<String> failedCards;

    @Schema(description = "When the dashboard was built (UTC)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant generatedAt;

    @Schema(description = "Whole months of the income, expenses and returns card; the other cards show the current "
            + "state (asOfDate) and do not use a period")
    private InvestorIncomeExpensesReturnsCardDTO.Period period;

    @Schema(description = "Value, equity and debt card (SCRUM-103)")
    private InvestorValueEquityDebtCardDTO valueEquityDebt;

    @Schema(description = "Income, expenses and returns card (SCRUM-104), over period")
    private InvestorIncomeExpensesReturnsCardDTO incomeExpensesReturns;

    @Schema(description = "Occupancy and exposure card (SCRUM-105)")
    private InvestorOccupancyExposureCardDTO occupancyExposure;

    @Schema(description = "Opportunities card (SCRUM-106), with its default limit and no county or state filter")
    private InvestorOpportunitiesCardDTO opportunities;

    @Schema(description = "How the dashboard is built")
    private String note;
}
