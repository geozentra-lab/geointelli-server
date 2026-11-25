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
     * Validates and queues an environmental reference load (SCRUM-201): one file per job, loaded with COPY.
     * FLOOD_ZONES needs the county of the file; the statewide datasets need no county and replace the state's rows.
     *
     * @param input        the file, stored under its own name (for example 12086_miami-dade.csv)
     * @param downloadedOn date the source was downloaded, recorded as the data vintage
     * @return the queued job
     * @throws IllegalArgumentException if the request or the file is invalid, or the first row is outside the scope
     * @throws org.springframework.web.server.ResponseStatusException 409 if migration V42 is missing, or a statewide
     *         job for the dataset is already active
     */
    ImportJobDTO startReference(IngestionDataset dataset, String county, String state, InputSource input,
            java.time.LocalDate downloadedOn, String createdBy);

    /**
     * Queues a Miami-Dade API ingestion job.
     * @throws org.springframework.web.server.ResponseStatusException 409 if one for the dataset is already active
     */
    ImportJobDTO startApi(IngestionDataset dataset, String createdBy);

    /**
     * Queues a school scoring job (SCRUM-203): {@link IngestionDataset#SCHOOL_ASSIGNMENT} assigns the schools of the
     * county, then computes its school score v2; {@link IngestionDataset#SCHOOL_SCORE_V2} computes the score v2 from
     * the stored assignments. Without {@code fips5}, the job covers every county with attendance zones (assignment)
     * or with stored assignments (score), one county per transaction.
     *
     * @param fips5 5-digit FIPS code of the county, or null for every county
     * @throws IllegalArgumentException if fips5 is not 5 digits or no county has it
     * @throws org.springframework.web.server.ResponseStatusException 409 if migration V49 is missing, the county has
     *         no attendance zones (assignment) or no assignment (score), or a job for the dataset is already active
     */
    ImportJobDTO startSchoolScoring(IngestionDataset dataset, String fips5, String createdBy);

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
