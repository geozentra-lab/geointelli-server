package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
/** Sum of recorded cash transactions in one category after ownership allocation. */
public record PortfolioFinancialCategoryDTO(FinancialRecordCategory category, FinancialRecordType type,
    BigDecimal amount, long recordCount) {}
