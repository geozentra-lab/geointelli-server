package com.geointelli.ai.property.service.enums;

import java.util.List;

/** Job statuses shared with the Mapillary backfill jobs so the admin view reads the same vocabulary. */
public final class IngestionJobStatus {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    /** Finished, but some records failed. */
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    /** Stopped before finishing: the worker lost its lease, was shut down or its queue deadline passed. */
    public static final String INTERRUPTED = "INTERRUPTED";

    public static final List<String> ALL = List.of(QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED, INTERRUPTED);
    public static final List<String> ACTIVE = List.of(QUEUED, RUNNING);
    /** Statuses a retry can resume from. */
    public static final List<String> RETRYABLE = List.of(PARTIAL, FAILED, INTERRUPTED);

    private IngestionJobStatus() { }
}
