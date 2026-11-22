package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.IngestionJobParameters;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.ReferenceDataImportService;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.InvalidReferenceRowException;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadRequest;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadResult;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs environmental reference jobs (SCRUM-201): one file per job, loaded by {@link ReferenceDataImportService} in
 * ONE transaction together with the job's counters and checkpoint. A failure leaves the previous rows of the county
 * or state untouched, and a retry simply loads the same stored file again (a load replaces its scope).
 */
@Service
@Slf4j
public class ReferenceDataIngestionJobWorker implements IngestionJobWorker {
    static final String INVALID_ROW = CsvIngestionJobWorker.INVALID_ROW;
    static final String INPUT_UNREADABLE = CsvIngestionJobWorker.INPUT_UNREADABLE;
    static final String DATABASE_ERROR = CsvIngestionJobWorker.DATABASE_ERROR;
    static final String LOAD_CHECK_FAILED = "LOAD_CHECK_FAILED";
    static final String SCOPE_UNAVAILABLE = "SCOPE_UNAVAILABLE";
    static final String LOADING = "{\"phase\":\"LOADING\"}";
    static final String LOADED = "{\"phase\":\"LOADED\"}";

    private final ReferenceDataImportService importer;
    private final ReferenceDataScopeService scopes;
    private final ObjectMapper objectMapper;

    public ReferenceDataIngestionJobWorker(ReferenceDataImportService importer, ReferenceDataScopeService scopes,
            ObjectMapper objectMapper) {
        this.importer = importer;
        this.scopes = scopes;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(IngestionJob job) {
        return job.getJobType() == ImportJobType.REFERENCE_DATA_IMPORT;
    }

    @Override
    public Result run(IngestionJobContext context) throws Exception {
        IngestionJob job = context.job();
        IngestionDataset dataset = job.getDataset();
        IngestionJobParameters parameters = parameters(job.getParametersJson());
        if (parameters.inputs() == null || parameters.inputs().size() != 1) {
            return Result.failed(INPUT_UNREADABLE, "A reference data job needs exactly one input file");
        }
        String name = parameters.inputs().get(0).name();
        LocalDate downloadedOn;
        try {
            downloadedOn = LocalDate.parse(parameters.downloadedOn());
        } catch (DateTimeParseException | NullPointerException missing) {
            return Result.failed(INPUT_UNREADABLE, "The job has no valid downloadedOn date");
        }
        String scope;
        try {
            scope = (dataset == IngestionDataset.FLOOD_ZONES
                ? scopes.countyFips(job.getCounty() == null ? null : job.getCounty().getId())
                : scopes.stateFips(job.getSourceState())).orElse(null);
        } catch (IllegalStateException unavailable) {
            return Result.failed(SCOPE_UNAVAILABLE, unavailable.getMessage());
        }
        if (scope == null) {
            return Result.failed(SCOPE_UNAVAILABLE, "No FIPS code for " + (dataset == IngestionDataset.FLOOD_ZONES
                ? "the county " + job.getSourceCounty() : "the state " + job.getSourceState()));
        }

        Path file;
        long rows;
        try {
            file = context.input(name);
            rows = importer.countRows(file);
        } catch (IOException | UncheckedIOException unreadable) {
            return Result.failed(INPUT_UNREADABLE, CsvIngestionJobWorker.sanitize(unreadable.getMessage()));
        }
        Counters counters = new Counters((int) rows, 0, 0, 0);
        context.progress(counters, name, LOADING);

        LoadRequest request = new LoadRequest(dataset, file, name, scope, downloadedOn, job.getId());
        LoadResult[] result = new LoadResult[1];
        try {
            context.commit(() -> {
                try {
                    result[0] = importer.load(request);
                } catch (IOException unreadable) {
                    throw new UncheckedIOException(unreadable);
                }
                return new Counters((int) rows, result[0].rowsLoaded(), result[0].rowsRead() - result[0].rowsLoaded(), 0);
            }, name, LOADED);
        } catch (InvalidReferenceRowException invalid) {
            return fail(context, counters, name + (invalid.row() > 0 ? ":" + invalid.row() : ""), INVALID_ROW, invalid.getMessage());
        } catch (UncheckedIOException unreadable) {
            return fail(context, counters, name, INPUT_UNREADABLE, unreadable.getMessage());
        } catch (DataAccessException databaseError) {
            return fail(context, counters, name, DATABASE_ERROR, databaseError.getMostSpecificCause().getMessage());
        } catch (IllegalStateException checkFailed) {
            return fail(context, counters, name, LOAD_CHECK_FAILED, checkFailed.getMessage());
        }
        log.info("Reference data job {} loaded {} ({} rows, {} table rows, {} without geometry)", job.getId(), name,
            result[0].rowsLoaded(), result[0].rowsWritten(), result[0].rowsWithoutGeometry());
        return Result.completed();
    }

    /** Records the failure as an item and a failed row; the file's transaction was rolled back. */
    private Result fail(IngestionJobContext context, Counters counters, String key, String reason, String message)
            throws InterruptedException {
        String text = CsvIngestionJobWorker.sanitize(message);
        context.item(key, "FAILED", reason, text);
        context.progress(counters.plus(0, 0, 1), key, LOADING);
        return Result.failed(reason, key + ": " + text);
    }

    private IngestionJobParameters parameters(String json) {
        if (json == null || json.isBlank()) return new IngestionJobParameters(null);
        try {
            return objectMapper.readValue(json, IngestionJobParameters.class);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("The job parameters cannot be read", invalid);
        }
    }
}
