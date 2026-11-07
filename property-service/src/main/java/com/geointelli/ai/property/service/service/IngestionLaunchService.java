package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Validates and queues ingestion jobs (SCRUM-188): CSV imports whose inputs are copied to the input store
 * before the request returns, Miami-Dade API ingestion, and retries that resume a finished job.
 */
public interface IngestionLaunchService {

    /** One input file to store for a job, under {@link #name()} (the dataset's file name). */
    interface InputSource {
        String name();
        long size();
        InputStream open() throws IOException;
    }

    /** Wraps an uploaded file; rejected when empty. */
    InputSource upload(MultipartFile file, String name);

    /**
     * Names a file inside the configured staging directory instead of uploading it.
     * @param relativePath file name or relative path inside the staging directory; must not leave it
     * @throws IllegalArgumentException if staging is disabled, the path escapes the directory or the file is missing
     */
    InputSource staged(String relativePath, String name);

    /** Uploaded county files for a folder job, named by their original file names. */
    List<InputSource> uploadedFolder(List<MultipartFile> files);

    /** County files found in a staged folder, by the names a folder import expects. */
    List<InputSource> stagedFolder(String relativePath);

    /**
     * Validates the inputs (county, state, CSV headers, required folder files), stores them and queues a job.
     * @param dataset a CSV dataset, including FOLDER
     * @param createdBy caller identity, or null
     * @return the queued job
     * @throws IllegalArgumentException if the request is invalid
     */
    ImportJobDTO startCsv(IngestionDataset dataset, String county, String state, List<InputSource> inputs,
            String createdBy);

    /**
     * Queues a Miami-Dade API ingestion job.
     * @throws org.springframework.web.server.ResponseStatusException 409 if one for the dataset is already active
     */
    ImportJobDTO startApi(IngestionDataset dataset, String createdBy);

    /**
     * Queues a new job that resumes a finished one from its checkpoint, on the same stored inputs.
     * @throws org.springframework.web.server.ResponseStatusException 404 if unknown; 409 with a reason code when refused
     */
    ImportJobDTO retry(UUID jobId, String createdBy);

    /**
     * Returns a job with its failed and skipped records.
     * @throws org.springframework.web.server.ResponseStatusException 404 if unknown
     */
    ImportJobDTO get(UUID jobId);
}
