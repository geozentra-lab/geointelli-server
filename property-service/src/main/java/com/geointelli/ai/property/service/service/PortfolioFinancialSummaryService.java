package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.PortfolioFinancialSummaryDTO;
/** Aggregates recorded USD cash flows in an inclusive transaction-date range. */
public interface PortfolioFinancialSummaryService {
    PortfolioFinancialSummaryDTO summarize(Long portfolioId, Long userId, LocalDate fromDate, LocalDate toDate);
}
