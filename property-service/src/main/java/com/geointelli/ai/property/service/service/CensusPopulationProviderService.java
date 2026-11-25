package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.EconomicProviderData;

import java.util.List;

/**
 * Provider adapter of the Census Bureau county population estimates (SCRUM-133): the file
 * co-est{vintage}-alldata.csv, with the population on July 1 and the net migration of each year since 2020.
 */
public interface CensusPopulationProviderService {
    /** Indicators this adapter reads. */
    List<String> INDICATORS = List.of("POPULATION", "NET_MIGRATION", "NET_DOMESTIC_MIGRATION",
            "NET_INTERNATIONAL_MIGRATION");

    /**
     * Downloads one vintage and reads the counties of one state. Every vintage re-estimates all the years since
     * 2020, so a newer vintage revises the values of an older one. Migration of 2020 is not read: it covers only
     * April 1 to June 30, 2020, not a full year.
     *
     * @param stateFips 2-digit state FIPS code, for example 12
     * @param vintage   year of the vintage, for example 2025 (values 2020 to 2025)
     * @throws IllegalArgumentException                                when the Census Bureau has not published the vintage
     * @throws org.springframework.web.server.ResponseStatusException 502 when the file cannot be downloaded or is
     *                                                                not the expected file
     */
    EconomicProviderData fetch(String stateFips, int vintage);
}
