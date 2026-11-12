package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.LenderLoanType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Approved, funded and loan-mix card of the lender dashboard (SCRUM-87 / SCRUM-114). Built from the
 * organization's recorded applications only; approved uses approvedAmount dated by approvedAt, funded uses
 * fundedAmount dated by fundedAt, and the loan mix splits exactly the funded applications by loan type.
 */
public record LenderApprovedFundedCardDTO(
        @Schema(description = "Organization the applications belong to", example = "42")
        Long organizationId,
        @Schema(description = "Currency of every amount", example = "USD")
        String currency,
        @Schema(description = "OK with at least one approved or funded application in the period, EMPTY otherwise, "
                + "ERROR when the composed dashboard could not load this card", example = STATUS_OK)
        String status,
        @Schema(description = "Why the card failed; null unless status is ERROR")
        String error,
        @Schema(description = "When the card was built (UTC)")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant generatedAt,
        @Schema(description = "Period the metrics cover")
        Period period,
        @Schema(description = "Applications approved in the period that are still APPROVED or FUNDED: "
                + "approvedAmount, dated by approvedAt")
        AmountTotal approved,
        @Schema(description = "Applications funded in the period: fundedAmount, dated by fundedAt")
        AmountTotal funded,
        @Schema(description = "The funded applications split by loan type; every type is listed, UNKNOWN included, "
                + "and the entries add up to funded")
        List<LoanMixEntry> fundedLoanMix,
        @Schema(description = "How the figures are built")
        String note
) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_ERROR = "ERROR";
    public static final String TIME_ZONE = "UTC";

    /** Inclusive calendar dates, interpreted in UTC. */
    public record Period(
            @Schema(description = "First day, included", example = "2026-10-01")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate fromDate,
            @Schema(description = "Last day, included", example = "2026-10-31")
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate toDate,
            @Schema(description = "Time zone of the dates", example = TIME_ZONE) String timeZone) {}

    /** Number of applications and their summed amount. */
    public record AmountTotal(
            @Schema(description = "Number of applications", example = "12") long count,
            @Schema(description = "Summed amount", example = "3600000.00") BigDecimal totalAmount) {}

    /** Funded applications of one loan type. */
    public record LoanMixEntry(
            @Schema(description = "Loan type; UNKNOWN when none was recorded", example = "FHA") LenderLoanType loanType,
            @Schema(description = "Number of funded applications", example = "2") long count,
            @Schema(description = "Summed funded amount", example = "540000.00") BigDecimal totalAmount) {}
}
