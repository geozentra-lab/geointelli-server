package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import com.geointelli.ai.property.service.dto.PortfolioValuationDTO;
/** Integration point for SCRUM-58; absent property IDs mean missing values, never zero. */
public interface PortfolioValuationService {
    Map<Long,PortfolioValuationDTO> latestValues(Set<Long> propertyIds, LocalDate asOfDate);
}
