package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.enums.ImportJobType;

import java.time.Instant;

/** Read-only counts of recorded rows for the admin coverage and provider-health dashboard (SCRUM-92). */
public interface CoverageHealthReader {

    /**
     * Counts all properties and, for each source, the properties with at least one recorded row.
     *
     * @return the counts; every value is 0 when there is no property
     */
    CoverageCounts countCoverage();

    /**
     * Reads the jobs of one type in {@code ingestion_jobs}.
     *
     * @param jobType CSV_IMPORT or PROPERTY_INGESTION
     * @return latest finished job, last completed job and active jobs; nulls when there is no such job
     */
    ProviderJobStats ingestionJobStats(ImportJobType jobType);

    /**
     * Reads the Mapillary jobs in {@code image_backfill_jobs}.
     *
     * @return latest finished job, last completed job and active jobs; nulls when there is no such job
     */
    ProviderJobStats imageBackfillJobStats();

    /** Total properties and properties with at least one row per source. */
    record CoverageCounts(long totalProperties, long addresses, long sales, long assessments, long taxes,
                          long images, long valuations) {}

    /** Latest finished job (status, end time, error code), last COMPLETED end time and QUEUED or RUNNING jobs. */
    record ProviderJobStats(String lastJobStatus, Instant lastJobAt, String lastErrorCode, Instant lastSuccessAt,
                            long activeJobs) {}
}
