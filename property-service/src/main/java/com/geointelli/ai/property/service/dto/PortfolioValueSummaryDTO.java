package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
/** Current reported financing and supported market-value coverage, never acquisition-price substitutes. */
public record PortfolioValueSummaryDTO(String currency,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate asOfDate,
    PortfolioMetricDTO value, PortfolioMetricDTO debt, PortfolioMetricDTO equity,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate oldestBalanceDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate newestBalanceDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime holdingsUpdatedAt,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate oldestValuationDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate newestValuationDate,
    List<String> valuationSources, String valuationSource, String debtSource, String note) {}
