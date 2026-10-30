package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.PortfolioValueSummaryDTO;
/** Summarizes ownership-adjusted market value, reported debt and covered equity. */
public interface PortfolioValueSummaryService {
    PortfolioValueSummaryDTO summarize(Long portfolioId, Long userId, LocalDate asOfDate);
}
