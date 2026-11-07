package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Loads Miami-Dade properties from the county API. The {@code ingest*} methods keep their original
 * behaviour (log and swallow failures); {@link #ingestForJob} reports outcomes to background jobs.
 */
public interface PropertyIngestionService {
    void ingest(String folio);

    void ingestAddresses(String folio);

    void ingestBuildings(String folio);

    void ingestExtraFeatures(String folio);

    void ingestSales(String folio);

    /** Outcome of one folio: written, or skipped with the reason (for example the property already exists). */
    record FolioResult(boolean ingested, String reason) {
        public static FolioResult written() { return new FolioResult(true, null); }
        public static FolioResult skipped(String reason) { return new FolioResult(false, reason); }
    }

    /** The county API could not be read; {@link #getCode()} is a stable reason code. */
    class SourceApiException extends RuntimeException {
        private final String code;

        public SourceApiException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    /**
     * Loads one folio for a job: checks the database first, calls the county API outside any transaction,
     * then writes in a short transaction. Nothing existing is overwritten.
     * @param dataset one of the API datasets
     * @throws SourceApiException if the county API fails or returns an unreadable response
     * @throws org.springframework.dao.DataAccessException if the database write fails
     */
    FolioResult ingestForJob(IngestionDataset dataset, String folio);
}
