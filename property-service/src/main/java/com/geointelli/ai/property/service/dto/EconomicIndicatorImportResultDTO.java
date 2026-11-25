package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of one economic indicator import (SCRUM-133). received = inserted + revised + unchanged + olderVintage.
 *
 * @param source         BLS_LAUS or CENSUS_PEP
 * @param stateFips      2-digit state FIPS code, for example 12 (Florida)
 * @param counties       counties requested (BLS) or found in the file (Census)
 * @param sourceUrl      where the values were read
 * @param sourceVintage  BLS: date the API was read; Census: vintage year
 * @param periodFrom     first period start received; null when nothing was received
 * @param periodTo       last period end received; null when nothing was received
 * @param received       values received from the source
 * @param inserted       new rows (a period never stored before)
 * @param revised        stored rows whose value or kind changed; the old value is kept in previousValue
 * @param unchanged      stored rows that already had this value; only lastVerifiedAt changed
 * @param olderVintage   values not written because the stored row comes from a newer vintage
 * @param missingValues  values the source did not give; nothing is stored for them
 * @param sourceMessages messages of the source (at most 20)
 * @param importedAt     time of the import, also ingestedAt/revisedAt/lastVerifiedAt of the rows written
 */
public record EconomicIndicatorImportResultDTO(
        String source, String stateFips, int counties, String sourceUrl,
        String sourceVintage, LocalDate periodFrom, LocalDate periodTo,
        int received, int inserted, int revised, int unchanged,
        int olderVintage, int missingValues, List<String> sourceMessages,
        Instant importedAt
) {
}
