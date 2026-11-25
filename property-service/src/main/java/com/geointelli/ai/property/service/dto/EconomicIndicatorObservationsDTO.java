package com.geointelli.ai.property.service.dto;

import java.util.List;

/**
 * Stored values of one indicator for one county (SCRUM-133).
 *
 * @param status       OK, or EMPTY when nothing is stored for this county and these years
 * @param observations oldest period first
 */
public record EconomicIndicatorObservationsDTO(
        String indicatorCode, String unit, String frequency,
        String geographyCode, Integer fromYear, Integer toYear,
        String status, List<EconomicIndicatorObservationDTO> observations

) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
}
