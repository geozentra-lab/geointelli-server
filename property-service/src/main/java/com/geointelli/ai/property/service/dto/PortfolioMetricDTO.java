package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
/** A complete total is null when any holding is uncovered; knownTotal remains a labelled partial sum. */
@Schema(description="USD allocated by ownership share. total is null for incomplete coverage; knownTotal sums only covered holdings. Empty portfolios have zero totals. Missing values never imply zero debt/value.")
public record PortfolioMetricDTO(String status, BigDecimal total, BigDecimal knownTotal,
    long coveredHoldings, long totalHoldings) {}
