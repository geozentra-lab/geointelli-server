package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
/** Grouping by holding preserves its ownership share and supports coverage without loading records. */
public record PortfolioFinancialAggregate(Long holdingId, BigDecimal share, FinancialRecordCategory category,
    String currency, BigDecimal amount, Long recordCount, LocalDateTime updatedAt) {}
