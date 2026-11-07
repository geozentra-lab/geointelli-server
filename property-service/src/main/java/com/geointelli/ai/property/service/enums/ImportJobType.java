package com.geointelli.ai.property.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Import job types known to the admin import-job tracking (SCRUM-187, SCRUM-188). Tracked types persist a job
 * with an ID, a status, counters and errors; the remaining ones are legacy loaders that run without a job record.
 */
@Getter
@AllArgsConstructor
public enum ImportJobType {
    /** Mapillary image backfill ({@code image_backfill_jobs}); numeric job IDs. */
    MAPILLARY_IMAGE_BACKFILL(true, "Background Mapillary image backfill persisted in image_backfill_jobs"),
    /** CSV file and folder imports ({@code /api/ingestion/import/*}); UUID job IDs in {@code ingestion_jobs}. */
    CSV_IMPORT(true, "Background CSV import persisted in ingestion_jobs"),
    /** Miami-Dade API ingestion ({@code /api/ingestion/run}, {@code /api/ingestion/ingest_*}); UUID job IDs. */
    PROPERTY_INGESTION(true, "Background Miami-Dade property ingestion persisted in ingestion_jobs"),
    /** Legacy address-matched image loader ({@code /api/images/import/*}); runs without a job record. */
    PROPERTY_IMAGE_CSV_IMPORT(false, "Legacy image CSV loader started without a job ID; no job record is persisted");

    /** Whether this type persists a job that can be listed and inspected. */
    private final boolean tracked;

    /** Short explanation shown to administrators. */
    private final String description;
}
