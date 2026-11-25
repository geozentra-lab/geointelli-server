package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;

/**
 * Imports economic indicators from their provider into the tables of migration V51 (SCRUM-133). One row per
 * indicator, county and period: importing a period again updates its row (a revision) and never adds a second one.
 */
public interface EconomicIndicatorImportService {

    /**
     * Imports the monthly labor force, employment and unemployment rate of the counties of a state from the BLS API.
     *
     * @param state    state code or name, for example FL
     * @param fips     one county only (5-digit FIPS code, for example 12086); null for every county of the state
     *                 with a FIPS code
     * @param fromYear first year, 1990 or later; null for two years before toYear
     * @param toYear   last year, not after the current year; null for the current year. At most 10 years.
     * @param loadedBy user who started the import, recorded in the load history
     * @return the counts of the import
     * @throws IllegalArgumentException for an unknown state or county, or invalid years
     */
    EconomicIndicatorImportResultDTO importBlsLaus(String state, String fips, Integer fromYear, Integer toYear,
                                                   String loadedBy);

    /**
     * Imports the population and net migration of the counties of a state from one Census vintage.
     *
     * @param state    state code or name, for example FL
     * @param vintage  vintage year, 2021 to the current year; null for the previous year
     * @param loadedBy user who started the import, recorded in the load history
     * @return the counts of the import
     * @throws IllegalArgumentException for an unknown state, an invalid vintage or one not published yet
     */
    EconomicIndicatorImportResultDTO importCensusPopulation(String state, Integer vintage, String loadedBy);
}
