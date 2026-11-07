package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.dto.CsvCheckpoint;
import com.geointelli.ai.property.service.dto.ImageSaveRequest;
import com.geointelli.ai.property.service.dto.IngestionJobParameters;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.service.CsvImportService;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.PropertyImageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs CSV import jobs file by file in batches: each batch and its checkpoint commit together, so a retry
 * resumes after the last committed batch. The owners step keeps its single transaction because owner rows
 * carry no source ID the second file could be matched against later. Image rows download outside the
 * transaction and save inside it. A bad row fails the job at that row; rows before it stay committed.
 */
@Service
@Slf4j
public class CsvIngestionJobWorker implements IngestionJobWorker {
    static final String INVALID_ROW = "INVALID_ROW";
    static final String MISSING_PROPERTY_MAPPING = "MISSING_PROPERTY_MAPPING";
    static final String DUPLICATE_FOLIO = "DUPLICATE_FOLIO";
    static final String DATABASE_CONSTRAINT = "DATABASE_CONSTRAINT";
    static final String DATABASE_ERROR = "DATABASE_ERROR";
    static final String INPUT_UNREADABLE = "INPUT_UNREADABLE";
    static final String BROKEN_IMAGE = "BROKEN_IMAGE";
    static final String DUPLICATE_IMAGE = "DUPLICATE_IMAGE";
    private static final Pattern RECORD_NUMBER = Pattern.compile("Invalid CSV record (\\d+) in ");
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setHeader()
        .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get();

    private final CsvImportService csvImportService;
    private final PropertyImageService propertyImageService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public CsvIngestionJobWorker(CsvImportService csvImportService, PropertyImageService propertyImageService,
            EntityManager entityManager, ObjectMapper objectMapper, IngestionProperties properties) {
        this.csvImportService = csvImportService;
        this.propertyImageService = propertyImageService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.batchSize = properties.getJobs().getCsvBatchSize();
    }

    @Override
    public boolean supports(IngestionJob job) {
        return job.getJobType() == ImportJobType.CSV_IMPORT;
    }

    @Override
    public Result run(IngestionJobContext context) throws Exception {
        IngestionJob job = context.job();
        String county = job.getSourceCounty();
        String state = job.getSourceState();
        List<IngestionDataset> steps = steps(job);
        CsvCheckpoint checkpoint = checkpoint(context.checkpointJson());
        Counters counters = context.counters();
        try {
            for (int index = checkpoint.stepIndex(); index < steps.size(); index++) {
                IngestionDataset step = steps.get(index);
                context.checkWorker();
                Path file = context.input(step.getFileName());
                if (!checkpoint.isCounted(index)) {
                    long rows = countRows(file);
                    if (step == IngestionDataset.OWNERS) rows += countRows(context.input(IngestionDataset.PROPERTY_OWNER_FILE));
                    counters = counters.withTotal(counters.total() + (int) rows);
                    checkpoint = checkpoint.counted(index);
                    context.progress(counters, step.getFileName(), json(checkpoint));
                }
                Rows rows = new Rows(context, counters, checkpoint, index, step.getFileName());
                switch (step) {
                    case OWNERS -> importOwners(rows, file, context.input(IngestionDataset.PROPERTY_OWNER_FILE), county, state);
                    case PROPERTIES -> {
                        rows.run(file, CsvCheckpoint.ROWS, csvImportService.propertyRowImporter(county, state), true);
                        if (csvImportService.hasParentLinks(file)) {
                            rows.run(file, CsvCheckpoint.PARENTS, csvImportService.parentLinkImporter(county, state), false);
                        }
                    }
                    case PROPERTY_IMAGES -> importImages(rows, file, county, state);
                    default -> rows.run(file, CsvCheckpoint.ROWS, csvImportService.relatedRowImporter(step, county, state), true);
                }
                counters = rows.counters;
                checkpoint = rows.checkpoint.atPhase(index + 1, CsvCheckpoint.ROWS);
            }
            context.progress(counters, "done", json(checkpoint));
            return counters.failed() > 0 ? Result.partial() : Result.completed();
        } catch (JobFailed failed) {
            return failed.result;
        } catch (IOException unreadable) {
            return Result.failed(INPUT_UNREADABLE, sanitize(unreadable.getMessage()));
        }
    }

    /** Files of the job in import order; a folder job only runs the files it was given. */
    private List<IngestionDataset> steps(IngestionJob job) {
        if (job.getDataset() != IngestionDataset.FOLDER) return List.of(job.getDataset());
        IngestionJobParameters parameters = parameters(job.getParametersJson());
        return IngestionDataset.FOLDER_ORDER.stream().filter(dataset -> parameters.has(dataset.getFileName())).toList();
    }

    /** owners.csv and property_owner.csv in one transaction, as the whole-file import always did. */
    private void importOwners(Rows rows, Path owners, Path propertyOwners, String county, String state)
            throws InterruptedException {
        long count;
        try {
            count = countRows(owners) + countRows(propertyOwners);
        } catch (IOException unreadable) {
            throw new JobFailed(Result.failed(INPUT_UNREADABLE, sanitize(unreadable.getMessage())));
        }
        try {
            rows.commitRows(() -> {
                try {
                    csvImportService.importOwners(owners, propertyOwners, county, state);
                } catch (IOException unreadable) {
                    throw new JobFailed(Result.failed(INPUT_UNREADABLE, sanitize(unreadable.getMessage())));
                }
                return new Delta((int) count, 0);
            }, count);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            rows.fail(rowNumber(invalid.getMessage()), reasonFor(invalid), invalid.getMessage());
        } catch (DataAccessException | PersistenceException databaseError) {
            rows.fail(null, databaseReason(databaseError), databaseError.getMessage());
        }
    }

    /** One image per transaction: the download happens before it, the save inside it. */
    private void importImages(Rows rows, Path file, String county, String state) throws InterruptedException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8); CSVParser parser = FORMAT.parse(reader)) {
            Iterator<CSVRecord> records = rows.resume(parser, CsvCheckpoint.ROWS);
            while (records.hasNext()) {
                CSVRecord record = records.next();
                rows.context.checkWorker();
                String key = rows.key(record.getRecordNumber());
                ImageRow image;
                try {
                    image = ImageRow.of(record);
                } catch (IllegalArgumentException invalid) {
                    rows.fail(record.getRecordNumber(), INVALID_ROW, invalid.getMessage());
                    return;
                }
                ImageSaveRequest download;
                try {
                    download = propertyImageService.download(image.url());
                } catch (ImageDownloadException broken) {
                    rows.context.item(key, "SKIPPED", BROKEN_IMAGE, sanitize(broken.getMessage()));
                    rows.commitRows(() -> new Delta(0, 1), 1);
                    continue;
                }
                try {
                    Delta delta = rows.commitRows(() -> {
                        Property property = csvImportService.mappedProperty(county, state, image.sourcePropertyId());
                        boolean saved = propertyImageService.saveDownloaded(property, download, image.primary(), image.order()) != null;
                        entityManager.flush();
                        entityManager.clear();
                        return saved ? new Delta(1, 0) : new Delta(0, 1);
                    }, 1);
                    if (delta.skipped() > 0) {
                        rows.context.item(key, "SKIPPED", DUPLICATE_IMAGE, "The property already has this image");
                    }
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    rows.fail(record.getRecordNumber(), reasonFor(invalid), invalid.getMessage());
                } catch (DataAccessException | PersistenceException databaseError) {
                    rows.fail(record.getRecordNumber(), databaseReason(databaseError), databaseError.getMessage());
                }
            }
        } catch (IOException unreadable) {
            throw new JobFailed(Result.failed(INPUT_UNREADABLE, sanitize(unreadable.getMessage())));
        }
    }

    /** Counter changes of one committed unit of work. */
    record Delta(int succeeded, int skipped) { }

    /** Batches of one file: reads rows, commits each batch with its checkpoint, and resumes from a checkpoint. */
    private final class Rows {
        private final IngestionJobContext context;
        private final int stepIndex;
        private final String file;
        private Counters counters;
        private CsvCheckpoint checkpoint;

        Rows(IngestionJobContext context, Counters counters, CsvCheckpoint checkpoint, int stepIndex, String file) {
            this.context = context;
            this.counters = counters;
            this.checkpoint = checkpoint;
            this.stepIndex = stepIndex;
            this.file = file;
        }

        void run(Path path, String phase, Consumer<CSVRecord> importer, boolean counted) throws InterruptedException {
            if (checkpoint.stepIndex() == stepIndex && CsvCheckpoint.PARENTS.equals(checkpoint.phase())
                    && CsvCheckpoint.ROWS.equals(phase)) return;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); CSVParser parser = FORMAT.parse(reader)) {
                Iterator<CSVRecord> records = resume(parser, phase);
                while (records.hasNext()) {
                    List<CSVRecord> batch = new ArrayList<>(batchSize);
                    while (records.hasNext() && batch.size() < batchSize) batch.add(records.next());
                    context.checkWorker();
                    long first = batch.get(0).getRecordNumber();
                    long last = batch.get(batch.size() - 1).getRecordNumber();
                    try {
                        commitRows(() -> {
                            for (CSVRecord record : batch) {
                                try {
                                    importer.accept(record);
                                } catch (IllegalArgumentException | IllegalStateException invalid) {
                                    throw new RowFailure(record.getRecordNumber(), invalid);
                                }
                            }
                            entityManager.flush();
                            entityManager.clear();
                            return new Delta(counted ? batch.size() : 0, 0);
                        }, batch.size());
                    } catch (RowFailure failure) {
                        fail(failure.row, reasonFor(failure.getCause()), failure.getCause().getMessage());
                    } catch (DataAccessException | PersistenceException databaseError) {
                        fail(null, databaseReason(databaseError), "rows " + first + "-" + last + ": " + databaseError.getMessage());
                    }
                }
            } catch (IOException unreadable) {
                throw new JobFailed(Result.failed(INPUT_UNREADABLE, sanitize(unreadable.getMessage())));
            }
        }

        /** Skips the rows the checkpoint already committed for this step and phase. */
        Iterator<CSVRecord> resume(CSVParser parser, String phase) {
            Iterator<CSVRecord> records = parser.iterator();
            long skip = checkpoint.committedRowsFor(stepIndex, phase);
            for (long i = 0; i < skip && records.hasNext(); i++) records.next();
            checkpoint = checkpoint.atPhase(stepIndex, phase).withCommitted(skip);
            return records;
        }

        /** Commits the work together with the counters it reports and the checkpoint advanced by {@code rows}. */
        Delta commitRows(Supplier<Delta> work, long rows) throws InterruptedException {
            CsvCheckpoint nextCheckpoint = checkpoint.withCommitted(checkpoint.committedRows() + rows);
            Delta[] delta = new Delta[1];
            counters = context.commit(() -> {
                delta[0] = work.get();
                return counters.plus(delta[0].succeeded(), delta[0].skipped(), 0);
            }, file, json(nextCheckpoint));
            checkpoint = nextCheckpoint;
            return delta[0];
        }

        String key(Long row) { return row == null ? file : file + ":" + row; }

        /** Records the failed row, keeps the checkpoint where it was and ends the job as FAILED. */
        void fail(Long row, String reason, String message) throws InterruptedException {
            String text = sanitize(message);
            context.item(key(row), "FAILED", reason, text);
            counters = counters.plus(0, 0, 1);
            context.progress(counters, file, json(checkpoint));
            throw new JobFailed(Result.failed(reason, key(row) + ": " + text));
        }
    }

    private record ImageRow(Long sourcePropertyId, String url, boolean primary, int order) {
        static ImageRow of(CSVRecord record) {
            Long sourcePropertyId = Long.valueOf(record.get("property_id").trim());
            String originalUrl = blankToNull(record.get("original_url"));
            Boolean primary = parseBoolean(record.get("primary_image"));
            Integer displayOrder = blankToNull(record.get("display_order")) == null ? null
                : Integer.valueOf(record.get("display_order").trim());
            if (displayOrder != null && displayOrder == 1) primary = true;
            if (originalUrl == null || primary == null || displayOrder == null) {
                throw new IllegalArgumentException("original_url, primary_image and display_order are required");
            }
            return new ImageRow(sourcePropertyId, originalUrl, primary, displayOrder);
        }

        private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

        private static Boolean parseBoolean(String value) {
            if (value == null || value.isBlank()) return null;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "true", "t", "1", "yes", "y" -> true;
                case "false", "f", "0", "no", "n" -> false;
                default -> throw new IllegalArgumentException("Invalid boolean: " + value);
            };
        }
    }

    private static final class RowFailure extends RuntimeException {
        private final long row;

        RowFailure(long row, RuntimeException cause) {
            super(cause);
            this.row = row;
        }
    }

    private static final class JobFailed extends RuntimeException {
        private final Result result;

        JobFailed(Result result) { this.result = result; }
    }

    private static String reasonFor(Throwable invalid) {
        return invalid instanceof IllegalStateException ? MISSING_PROPERTY_MAPPING : INVALID_ROW;
    }

    private static String databaseReason(RuntimeException error) {
        String text = String.valueOf(error.getMessage()) + " " + (error.getCause() == null ? "" : error.getCause().getMessage());
        if (text.contains("uq_properties_county_folio")) return DUPLICATE_FOLIO;
        if (error instanceof DataIntegrityViolationException || text.contains("constraint")) return DATABASE_CONSTRAINT;
        return DATABASE_ERROR;
    }

    private static Long rowNumber(String message) {
        if (message == null) return null;
        Matcher matcher = RECORD_NUMBER.matcher(message);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    /** Removes server paths and control characters; the job's detail view must not reveal the file system. */
    static String sanitize(String message) {
        if (message == null) return null;
        String text = message.replace("\u0000", "").replaceAll("[A-Za-z]:\\\\[^\\s:]+|/[^\\s:]+/[^\\s:]+", "<path>");
        return text.length() > 480 ? text.substring(0, 480) : text;
    }

    private static long countRows(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8); CSVParser parser = FORMAT.parse(reader)) {
            long rows = 0;
            for (CSVRecord ignored : parser) rows++;
            return rows;
        }
    }

    private CsvCheckpoint checkpoint(String json) {
        if (json == null || json.isBlank()) return CsvCheckpoint.start();
        try {
            return objectMapper.readValue(json, CsvCheckpoint.class);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("The job checkpoint cannot be read", invalid);
        }
    }

    private IngestionJobParameters parameters(String json) {
        if (json == null || json.isBlank()) return new IngestionJobParameters(List.of());
        try {
            return objectMapper.readValue(json, IngestionJobParameters.class);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("The job parameters cannot be read", invalid);
        }
    }

    private String json(CsvCheckpoint checkpoint) {
        try {
            return objectMapper.writeValueAsString(checkpoint);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException(invalid);
        }
    }
}
