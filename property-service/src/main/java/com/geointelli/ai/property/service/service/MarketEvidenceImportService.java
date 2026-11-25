package com.geointelli.ai.property.service.service;

import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVRecord;

/**
 * Imports market sale and asking-price observations (SCRUM-131) from {@code market_evidence.csv}. Each row
 * describes one geography and one period for one source; the importer keeps sale and asking figures apart,
 * records provenance and freshness, and updates an existing observation instead of duplicating it, so the
 * same file can be imported again without changing the row count.
 *
 * <p>Expected columns: {@code source, evidence_type, geography_level, geography_code, period_start,
 * period_end} always; {@code geography_name, source_dataset, source_release_date, observation_kind, currency}
 * optionally; the SALE figures {@code sale_count, median_sale_price, average_sale_price,
 * median_sale_price_per_sqft} or the ASKING figures {@code active_listing_count, new_listing_count,
 * median_asking_price, average_asking_price, median_asking_price_per_sqft, median_days_on_market,
 * price_reduced_count}, whichever the row's type allows.
 */
public interface MarketEvidenceImportService {

    /**
     * Row importer used by the background CSV job: validates one record, resolves its state and county when
     * they match ours, and inserts or updates the observation.
     *
     * @param jobId the ingestion job writing the rows, recorded on each observation as provenance
     * @return a consumer that throws {@link IllegalArgumentException} for an invalid row
     */
    Consumer<CSVRecord> rowImporter(UUID jobId);
}
