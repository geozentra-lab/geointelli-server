package com.geointelli.ai.property.service.service;

import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVRecord;

/**
 * Imports rent observations (SCRUM-132) from {@code rent_evidence.csv}. Each row describes one geography, one
 * period, one rent type and one rent period for one source. The importer keeps asking rent (advertised) and
 * achieved rent (paid) apart, records the currency, the rent period, the geography and the source on every row,
 * never converts a monthly figure into an annual one or the reverse, and updates an existing observation
 * instead of duplicating it, so the same file can be imported again without changing the row count.
 *
 * <p>Expected columns: {@code source, evidence_type, geography_level, geography_code, period_start, period_end,
 * rent_period} always; {@code geography_name, state_code, county_name, source_dataset, source_release_date,
 * observation_kind, currency, bedrooms, median_rent_moe, sample_count} optionally; and at least one of the
 * amounts {@code median_rent, average_rent, median_rent_per_sqft}, all expressed per the row's
 * {@code rent_period}.
 */
public interface RentEvidenceImportService {

    /**
     * Row importer used by the background CSV job: validates one record, resolves its state and county when
     * they match ours, and inserts or updates the observation.
     *
     * @param jobId the ingestion job writing the rows, recorded on each observation as provenance
     * @return a consumer that throws {@link IllegalArgumentException} for an invalid row, with the reason
     */
    Consumer<CSVRecord> rowImporter(UUID jobId);
}
