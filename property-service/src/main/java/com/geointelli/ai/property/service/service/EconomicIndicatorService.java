package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationsDTO;

import java.util.List;

/**
 * Reads the stored economic indicators (SCRUM-133): their definitions with the coverage, and the values of one
 * county. U.S. federal statistics: no user owns them.
 */
public interface EconomicIndicatorService {

    /**
     * Every supported indicator with what is stored for it (counties, periods, freshness).
     *
     * @return the indicators, BLS first; coverage counts are 0 before the first import
     */
    List<EconomicIndicatorDTO> getIndicators();

    /**
     * The stored values of one indicator for one county.
     *
     * @param code     indicator code, for example UNEMPLOYMENT_RATE (case is ignored)
     * @param fips     5-digit county FIPS code, for example 12086
     * @param fromYear only periods ending in this year or later; null for no limit
     * @param toYear   only periods ending in this year or earlier; null for no limit
     * @return the values, oldest first; status EMPTY when none is stored
     * @throws IllegalArgumentException                               for an invalid fips or years
     * @throws org.springframework.web.server.ResponseStatusException 404 for an unknown indicator
     */
    EconomicIndicatorObservationsDTO getObservations(String code, String fips, Integer fromYear, Integer toYear);
}
