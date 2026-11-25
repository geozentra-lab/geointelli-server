package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import com.geointelli.ai.property.service.dto.RentEvidenceResponseDTO;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentPeriod;

/**
 * Reads imported rent evidence (SCRUM-132) for one geography, newest period first, with a coverage report.
 * It never combines asking and achieved rent into one figure, and never mixes monthly and annual amounts: they
 * are returned as published unless the caller asks for monthly values, in which case every converted row is
 * marked with the period it was converted from.
 */
public interface RentEvidenceQueryService {

    /**
     * One page of rent observations for a geography, and which rent types are covered.
     *
     * @param level geography level (required)
     * @param code geography code in the level's format: two-letter state, five-digit county FIPS or ZIP, or
     *             eleven-digit census tract (required)
     * @param type ASKING or ACHIEVED, or null for both; coverage is reported for the requested types
     * @param source provider code, or null for all sources
     * @param rentPeriod keep only rows published MONTHLY or ANNUAL, or null for both
     * @param from keep periods ending on or after this date, or null
     * @param to keep periods starting on or before this date, or null
     * @param asMonthly true to return ANNUAL rows divided by 12 and marked {@code convertedFrom = ANNUAL}
     * @param page zero-based page number
     * @param size rows per page, 1 to 200
     * @return the page and the coverage; NO_COVERAGE with an empty page when nothing matches
     * @throws IllegalArgumentException if the code does not fit the level, the window is inverted or the paging
     *         is out of range
     */
    RentEvidenceResponseDTO find(RentGeographyLevel level, String code, RentEvidenceType type, String source,
            RentPeriod rentPeriod, LocalDate from, LocalDate to, boolean asMonthly, int page, int size);
}
