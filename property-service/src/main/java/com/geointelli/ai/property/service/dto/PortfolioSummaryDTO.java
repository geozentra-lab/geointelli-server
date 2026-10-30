package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
/** Composite report; unavailable sections remain null with a corresponding partial error. */
public record PortfolioSummaryDTO(Long portfolioId, String portfolioName, String status,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Instant generatedAt,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate asOfDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fromDate,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate toDate,
    PortfolioValueSummaryDTO valuationAndDebt, PortfolioFinancialSummaryDTO financials,
    PortfolioExposureSummaryDTO occupancyAndExposure, List<PortfolioSummaryErrorDTO> errors) {}
