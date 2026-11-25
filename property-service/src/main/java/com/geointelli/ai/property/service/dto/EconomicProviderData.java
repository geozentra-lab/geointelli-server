package com.geointelli.ai.property.service.dto;

import java.util.List;

/**
 * What a provider adapter read from its source (SCRUM-133).
 *
 * @param sourceUrl      where the values were read
 * @param sourceVintage  BLS: date the API was read (yyyy-MM-dd); Census: vintage year (2025)
 * @param observations   the values the source gave
 * @param missingValues  values the source did not give (BLS "-", an empty Census cell); they are not stored
 * @param sourceMessages messages of the source, for example "No Data Available for Series ... Year: 2026"
 */
public record EconomicProviderData(
        String sourceUrl, String sourceVintage, List<EconomicObservation> observations,
        int missingValues, List<String> sourceMessages
) {
}
