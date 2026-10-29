package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.dto.PortfolioValuationDTO;
import com.geointelli.ai.property.service.service.PortfolioValuationService;
/** SCRUM-58 is not implemented. Legacy predictions lack its supported valuation contract and currency provenance. */
@Service
public class PortfolioValuationServiceImpl implements PortfolioValuationService {
    @Override
    public Map<Long,PortfolioValuationDTO> latestValues(Set<Long> propertyIds, LocalDate asOfDate) {
        return Map.of();
    }
}
