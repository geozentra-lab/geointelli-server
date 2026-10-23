package com.geointelli.ai.property.service.repository;

/** Holding counts for one page of portfolios without a query per portfolio. */
public interface PortfolioHoldingCount {
    Long getPortfolioId();
    long getHoldingCount();
}
