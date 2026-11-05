package com.geointelli.ai.property.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Import job types known to the admin import-job tracking (SCRUM-187). Only tracked types persist a job
 * with an ID, a status, counters and errors; the other ones are legacy imports that run without a job record.
 */
@Getter
@AllArgsConstructor
public enum ImportJobType {
    /** Mapillary image backfill ({@code image_backfill_jobs}); runs in the background and returns a job ID. */
    MAPILLARY_IMAGE_BACKFILL(true, "Background Mapillary image backfill persisted in image_backfill_jobs"),
    /** CSV imports ({@code /api/ingestion/import/*}); run synchronously and persist no job. */
    CSV_IMPORT(false, "Synchronous CSV import; no job record is persisted"),
    /** County property ingestion ({@code /api/ingestion/run}, {@code /api/ingestion/ingest_*}); started without a job ID. */
    PROPERTY_INGESTION(false, "Property ingestion started without a job ID; no job record is persisted");

    /** Whether this type persists a job that can be listed and inspected. */
    private final boolean tracked;

    /** Short explanation shown to administrators. */
    private final String description;
}
