package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One value read from a provider (SCRUM-133), before it is stored: one indicator, one county, one period.
 *
 * @param indicatorCode   code of the indicator, for example UNEMPLOYMENT_RATE
 * @param geographyCode   5-digit county FIPS code, for example 12086 (Miami-Dade)
 * @param geographyName   county name as the source writes it; may be null
 * @param periodStart     first day of the period (a month, July 1 for the population, July 1 for migration)
 * @param periodEnd       last day of the period
 * @param value           the value in the indicator unit; never null (a missing value is not an observation)
 * @param observationKind ACTUAL (observed by the source) or ESTIMATE (modelled or preliminary)
 * @param sourceSeriesId  the source series ID (BLS), or the column name (Census)
 */
public record EconomicObservation(
        String indicatorCode, String geographyCode, String geographyName,
        LocalDate periodStart, LocalDate periodEnd, BigDecimal value,
        String observationKind, String sourceSeriesId
) {
    public static final String ACTUAL = "ACTUAL";
    public static final String ESTIMATE = "ESTIMATE";
}
