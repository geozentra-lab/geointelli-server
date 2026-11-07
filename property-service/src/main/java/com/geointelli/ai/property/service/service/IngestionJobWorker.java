package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.IngestionJob;

/** Runs one claimed ingestion job of the types it supports. */
public interface IngestionJobWorker {

    /** Final outcome of a run that ended on its own; interruptions and lease loss are reported by exceptions. */
    record Result(String status, String errorCode, String errorMessage) {
        public static Result completed() { return new Result("COMPLETED", null, null); }
        public static Result partial() { return new Result("PARTIAL", null, null); }
        public static Result failed(String errorCode, String errorMessage) { return new Result("FAILED", errorCode, errorMessage); }
    }

    boolean supports(IngestionJob job);

    /**
     * Processes the job through the context, which commits data and progress together.
     * @throws IngestionLeaseLostException when a progress write found the lease gone
     * @throws InterruptedException when the worker thread is stopped
     */
    Result run(IngestionJobContext context) throws Exception;
}
