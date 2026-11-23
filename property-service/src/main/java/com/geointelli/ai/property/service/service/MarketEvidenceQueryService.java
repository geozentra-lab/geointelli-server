package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.MarketEvidenceObservationDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;

/**
 * Reads imported market evidence (SCRUM-131) for one geography, newest period first. Used to verify an
 * import and by later market features; it never mixes sale and asking rows into one figure.
 */
public interface MarketEvidenceQueryService {

    /**
     * One page of observations for a market.
     *
     * @param level geography level of the market (required)
     * @param code geography code in the level's format: two-letter state, five-digit county FIPS or ZIP (required)
     * @param type SALE or ASKING, or null for both
     * @param source provider code, or null for all sources
     * @param from keep periods ending on or after this date, or null
     * @param to keep periods starting on or before this date, or null
     * @param page zero-based page number
     * @param size rows per page, 1 to 200
     * @return the page, empty when nothing has been imported for the market
     * @throws IllegalArgumentException if the code does not fit the level, the window is inverted or the paging is out of range
     */
    PageResponse<MarketEvidenceObservationDTO> find(MarketGeographyLevel level, String code, MarketEvidenceType type,
            String source, LocalDate from, LocalDate to, int page, int size);
}
