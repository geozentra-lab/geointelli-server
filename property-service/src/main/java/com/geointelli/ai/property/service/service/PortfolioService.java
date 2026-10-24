package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.dto.PortfolioRequest;

/** Creates and manages portfolios owned by the authenticated user. */
public interface PortfolioService {
    /** Creates a portfolio; ownership is resolved from the bearer token. */
    PortfolioDTO create(PortfolioRequest request);
    /** Lists only the current user's portfolios, newest ID first. */
    PortfolioPageDTO<PortfolioDTO> list(int page, int size);
    /** Returns an owned portfolio, or 404 for an absent or foreign portfolio. */
    PortfolioDTO get(Long portfolioId);
    /** Changes only supplied fields; null description clears it. */
    PortfolioDTO update(Long portfolioId, PortfolioRequest request);
    /** Permanently deletes the portfolio and its holdings, preserving underlying properties and images. */
    void delete(Long portfolioId);
}
