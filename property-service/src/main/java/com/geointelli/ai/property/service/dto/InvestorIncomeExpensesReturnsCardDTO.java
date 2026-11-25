package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Income, expenses and returns card of the investor dashboard (SCRUM-85 / SCRUM-104). Recorded cash (actual) and
 * contract-rent figures (projected) are two separate sections over the same whole months; they are never added
 * together.
 */
@Data
public class InvestorIncomeExpensesReturnsCardDTO {

    public static final String SCOPE_ALL_PORTFOLIOS = "ALL_PORTFOLIOS";
    public static final String SCOPE_PORTFOLIO = "PORTFOLIO";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_ERROR = "ERROR";
    public static final String ACTUAL_BASIS = "ACTUAL_RECORDED_CASH";
    public static final String ACTUAL_NO_RECORDS = "NO_RECORDS";
    public static final String ACTUAL_RECORDED = "RECORDED";
    public static final String PROJECTED_BASIS = "CONTRACT_RENT_INVESTMENT_CALCULATION";
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String TIME_ZONE = "UTC";

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

    @Schema(description = "Currency of every amount", example = "USD")
    private String currency;

    @Schema(description = "Whole months covered by both the actual and the projected figures")
    private Period period;

    @Schema(description = "Number of holdings in the included portfolios; null on an ERROR card", example = "4")
    private Long totalHoldings;

    @Schema(description = "Recorded cash of the period (financial records)")
    private Actual actual;

    @Schema(description = "Contract-rent figures for the same months, from the investment calculation")
    private Projected projected;

    @Schema(description = "How the figures are built")
    private String note;

    /** Whole calendar months, both included, in UTC. */
    public record Period(
            @Schema(description = "First month, included", example = "2025-11")
            @JsonFormat(shape = JsonFormat.Shape.STRING) YearMonth fromMonth,
            @Schema(description = "Last month, included", example = "2026-10")
            @JsonFormat(shape = JsonFormat.Shape.STRING) YearMonth toMonth,
            @Schema(description = "First day of fromMonth", example = "2025-11-01")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate fromDate,
            @Schema(description = "Last day of toMonth", example = "2026-10-31")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate toDate,
            @Schema(description = "Number of months", example = "12") int months,
            @Schema(description = "Time zone of the dates", example = TIME_ZONE) String timeZone) {}

    /** Recorded income and expenses of the period, after ownership share; nothing is projected here. */
    public record Actual(
            @Schema(description = "Where the figures come from", example = ACTUAL_BASIS) String basis,
            @Schema(description = "RECORDED with at least one record in the period, NO_RECORDS otherwise",
                    example = ACTUAL_RECORDED) String status,
            @Schema(description = "Recorded income", example = "40500.00") BigDecimal income,
            @Schema(description = "Recorded expenses", example = "14000.00") BigDecimal expenses,
            @Schema(description = "income - expenses; not profit", example = "26500.00") BigDecimal cashFlow,
            @Schema(description = "Number of records", example = "38") long recordCount,
            @Schema(description = "Holdings with at least one record in the period", example = "2")
            long holdingsWithRecords,
            @Schema(description = "Holdings without any record in the period", example = "0")
            long holdingsWithoutRecords,
            @Schema(description = "Recorded amount per category") List<PortfolioFinancialCategoryDTO> categories,
            @Schema(description = "Expense categories with no record in the period: a missing cost, not a cost of 0")
            List<FinancialRecordCategory> expenseCategoriesWithoutRecords,
            @Schema(description = "Latest change of an included record")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime recordsUpdatedAt) {}

    /**
     * Contract rent of the rented holdings run through the SCRUM-64 investment calculation. monthlyGrossRent,
     * annualGrossRent and grossYieldPercent are today's contract figures; periodGrossRent counts each holding only
     * for the part of the period it was held and leased.
     */
    public record Projected(
            @Schema(description = "Where the figures come from", example = PROJECTED_BASIS) String basis,
            @Schema(description = "AVAILABLE when at least one rented holding has a contract rent, UNAVAILABLE "
                    + "otherwise", example = AVAILABLE) String status,
            @Schema(description = "Holdings reported as RENTED", example = "2") long rentedHoldings,
            @Schema(description = "Rented holdings with a monthly contract rent", example = "2") long holdingsWithRent,
            @Schema(description = "Current monthly contract rent after ownership share", example = "3500.00")
            BigDecimal monthlyGrossRent,
            @Schema(description = "monthlyGrossRent x 12 (current)", example = "42000.00") BigDecimal annualGrossRent,
            @Schema(description = "Contract rent of the period: each holding only from its acquisition date and "
                    + "lease start to its lease end, a partly covered month in proportion to its days; comparable "
                    + "with actual.income", example = "42000.00") BigDecimal periodGrossRent,
            @Schema(description = "annualGrossRent / purchase price x 100 (current); null when a purchase price is "
                    + "missing", example = "8.40") BigDecimal grossYieldPercent,
            @Schema(description = "Always null: holdings store no operating costs, so NOI cannot be projected")
            BigDecimal netOperatingIncome,
            @Schema(description = "Inputs the projection does not have") List<String> missingInputs,
            @Schema(description = "Why the projection is unavailable; null when AVAILABLE") String reason) {}
}
