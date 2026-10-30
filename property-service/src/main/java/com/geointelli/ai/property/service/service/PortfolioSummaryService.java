package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.PortfolioSummaryDTO;
/** Composes component summaries after checking the authenticated user's portfolio ownership. */
public interface PortfolioSummaryService {
    PortfolioSummaryDTO summarize(Long portfolioId, LocalDate fromDate, LocalDate toDate);
}
