package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.PortfolioExposureSummaryDTO;
/** Summarizes recorded current occupancy and holding-count geography. */
public interface PortfolioExposureSummaryService {
    PortfolioExposureSummaryDTO summarize(Long portfolioId, Long userId, LocalDate asOfDate);
}
