package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.EconomicProviderData;

import java.util.List;
import java.util.Map;

/**
 * Provider adapter of the BLS Local Area Unemployment Statistics API (SCRUM-133): monthly labor force, employment
 * and unemployment rate of counties, not seasonally adjusted.
 */
public interface BlsLausProviderService {

    /** Indicators this adapter reads. */
    List<String> INDICATORS = List.of("LABOR_FORCE", "EMPLOYMENT", "UNEMPLOYMENT_RATE");

    /**
     * Reads the monthly values of the counties. Annual averages (period M13) are not read: monthly and annual values
     * are never mixed. A value the API gives as "-" is counted in missingValues and not returned.
     *
     * @param counties county name by 5-digit FIPS code, for example 12086 to Miami-Dade
     * @param fromYear first year, included
     * @param toYear   last year, included; at most 10 years after fromYear
     * @return the values, the source messages ("No Data Available for Series ...") and the date of the read
     * @throws org.springframework.web.server.ResponseStatusException 502 when the API is unavailable, refuses the
     *                                                                request (daily limit) or answers unreadably
     */
    EconomicProviderData fetch(Map<String, String> counties, int fromYear, int toYear);

}
