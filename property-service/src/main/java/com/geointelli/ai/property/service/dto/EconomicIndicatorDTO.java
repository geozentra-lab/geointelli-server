package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Definition of an economic indicator and what is stored for it (SCRUM-133).
 *
 * @param code           for example UNEMPLOYMENT_RATE
 * @param unit           PERSONS or PERCENT
 * @param frequency      MONTHLY or ANNUAL; monthly and annual values are never mixed in one indicator
 * @param geographyLevel COUNTY, the smallest geography where every series is published for every location
 * @param source         BLS_LAUS or CENSUS_PEP
 * @param coverage       what is stored; counts are 0 and dates null before the first import
 */
public record EconomicIndicatorDTO(
        String code, String name, String description, String unit, String frequency,
        String geographyLevel, String source, String sourceName, String sourceUrl,
        Coverage coverage
) {
    /**
     * Stored observations of the indicator.
     *
     * @param counties             counties with at least one value
     * @param observations         stored values
     * @param estimateObservations of those, values of kind ESTIMATE (modelled or preliminary)
     * @param firstPeriod          start of the oldest period stored
     * @param lastPeriod           end of the newest period stored
     * @param lastVerifiedAt       last time an import confirmed a value (freshness)
     * @param lastRevisedAt        last time an import changed a value
     */
    public record Coverage(int counties, int observations, int estimateObservations, LocalDate firstPeriod,
                           LocalDate lastPeriod, Instant lastVerifiedAt, Instant lastRevisedAt) {
    }
}
