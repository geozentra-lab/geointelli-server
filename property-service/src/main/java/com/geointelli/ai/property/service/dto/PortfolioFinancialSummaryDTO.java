package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
/** Actual recorded cash flows, not projected rent, profit or an investment-return estimate. */
public record PortfolioFinancialSummaryDTO(String status, String basis, String currency,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fromDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate toDate,
    BigDecimal income, BigDecimal expenses, BigDecimal cashFlow,
    long recordCount, long holdingsWithRecords, long totalHoldings,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime recordsUpdatedAt,
    List<PortfolioFinancialCategoryDTO> categories, String note) {}
