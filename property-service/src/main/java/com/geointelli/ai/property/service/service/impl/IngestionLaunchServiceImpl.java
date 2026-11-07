package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.IngestionJobParameters;
import com.geointelli.ai.property.service.dto.IngestionJobParameters.StoredInputRef;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.mapper.IngestionJobMapper;
import com.geointelli.ai.property.service.service.CountyService;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionInputStore.StoredInput;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobWorkerService;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rejects bad requests before anything is stored, copies the inputs into the store under the new job's
 * location, inserts the QUEUED job and nudges the worker. A retry copies the finished job and its checkpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionLaunchServiceImpl implements IngestionLaunchService {
    static final int ITEM_LIMIT = 1000;
    static final String INPUT_UNAVAILABLE = "INPUT_UNAVAILABLE";
    static final String INPUT_CHANGED = "INPUT_CHANGED";
    static final String DATASET_JOB_ACTIVE = "DATASET_JOB_ACTIVE";
    private static final CSVFormat HEADER_FORMAT = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();

    private final IngestionJobService jobService;
    private final IngestionInputStore inputStore;
    private final IngestionJobWorkerService workerService;
    private final IngestionJobMapper mapper;
    private final CountyService countyService;
    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public InputSource upload(MultipartFile file, String name) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("The uploaded file " + name + " is empty");
        return new InputSource() {
            @Override public String name() { return name; }
            @Override public long size() { return file.getSize(); }
            @Override public InputStream open() throws IOException { return file.getInputStream(); }
        };
    }

    @Override
    public InputSource staged(String relativePath, String name) {
        Path file = stagedPath(relativePath);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("Staged file not found: " + relativePath);
        }
        return pathSource(file, name);
    }

    @Override
    public List<InputSource> uploadedFolder(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("No files were uploaded");
        Set<String> known = folderFileNames();
        List<InputSource> inputs = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "" : Path.of(file.getOriginalFilename()).getFileName().toString();
            if (!known.contains(name)) {
                throw new IllegalArgumentException("Unexpected file " + name + "; a folder import accepts " + known);
            }
            inputs.add(upload(file, name));
        }
        return inputs;
    }

    @Override
    public List<InputSource> stagedFolder(String relativePath) {
        Path folder = stagedPath(relativePath);
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Staged folder not found: " + relativePath);
        List<InputSource> inputs = new ArrayList<>();
        for (String name : folderFileNames()) {
            Path file = folder.resolve(name);
            if (Files.isRegularFile(file)) inputs.add(pathSource(file, name));
        }
        return inputs;
    }

    @Override
    public ImportJobDTO startCsv(IngestionDataset dataset, String county, String state, List<InputSource> inputs,
            String createdBy) {
        if (dataset == null || dataset.getJobType() != ImportJobType.CSV_IMPORT) {
            throw new IllegalArgumentException("Not a CSV dataset: " + dataset);
        }
        County resolved = resolveCounty(dataset, county, state);
        validateInputs(dataset, inputs);

        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.CSV_IMPORT);
        job.setDataset(dataset);
        job.setStatus(IngestionJobStatus.QUEUED);
        job.setSourceCounty(county.trim());
        job.setSourceState(state.trim());
        job.setCounty(resolved);
        job.setCreatedBy(createdBy);
        storeInputs(job, inputs);
        return queue(job);
    }

    @Override
    public ImportJobDTO startApi(IngestionDataset dataset, String createdBy) {
        if (dataset == null || dataset.getJobType() != ImportJobType.PROPERTY_INGESTION) {
            throw new IllegalArgumentException("Not an API dataset: " + dataset);
        }
        requireNoActiveJob(dataset);
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.PROPERTY_INGESTION);
        job.setDataset(dataset);
        job.setStatus(IngestionJobStatus.QUEUED);
        job.setCounty(countyService.miamiDade());
        job.setSourceCounty("Miami-Dade");
        job.setSourceState("FL");
        job.setInputStorage("NONE");
        job.setCreatedBy(createdBy);
        return queue(job);
    }

    @Override
    public ImportJobDTO retry(UUID jobId, String createdBy) {
        IngestionJob previous = jobService.find(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));
        String blocked = mapper.retryBlockedReason(previous.getStatus(), jobService.hasActiveRetry(jobId),
            previous.getInputStorage(), previous.getInputScope());
        if (blocked != null) throw refused(blocked, previous);
        if (previous.getJobType() == ImportJobType.PROPERTY_INGESTION) requireNoActiveJob(previous.getDataset());
        verifyInputs(previous);

        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(previous.getJobType());
        job.setDataset(previous.getDataset());
        job.setStatus(IngestionJobStatus.QUEUED);
        job.setSourceCounty(previous.getSourceCounty());
        job.setSourceState(previous.getSourceState());
        job.setCounty(previous.getCounty());
        job.setInputStorage(previous.getInputStorage());
        job.setInputLocation(previous.getInputLocation());
        job.setInputScope(previous.getInputScope());
        job.setParametersJson(previous.getParametersJson());
        job.setAttempt(previous.getAttempt() + 1);
        job.setRetryOfJobId(previous.getId());
        job.setCreatedBy(createdBy);
        if (previous.getJobType() == ImportJobType.CSV_IMPORT) {
            // Resume: keep what was counted and committed; the failed count belongs to the previous attempt.
            job.setCheckpointJson(previous.getCheckpointJson());
            job.setCurrentStep(previous.getCurrentStep());
            job.setTotal(previous.getTotal());
            job.setSucceeded(previous.getSucceeded());
            job.setSkipped(previous.getSkipped());
            job.setProcessed(previous.getSucceeded() + previous.getSkipped());
        }
        return queue(job);
    }

    @Override
    public ImportJobDTO get(UUID jobId) {
        IngestionJob job = jobService.find(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));
        ImportJobDTO dto = mapper.toDTO(job);
        dto.setItems(jobService.items(jobId, ITEM_LIMIT));
        return dto;
    }

    /** Properties and folder imports may create the county; the related files need it to exist already. */
    private County resolveCounty(IngestionDataset dataset, String county, String state) {
        if (county == null || county.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("county and sourceState are required");
        }
        if (dataset == IngestionDataset.PROPERTIES || dataset == IngestionDataset.FOLDER) {
            countyService.ensureExists(county, state);
        }
        return countyService.resolve(county, state);
    }

    private void validateInputs(IngestionDataset dataset, List<InputSource> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("At least one input file is required");
        Set<String> names = new HashSet<>();
        for (InputSource input : inputs) {
            if (!names.add(input.name())) throw new IllegalArgumentException("Duplicate input file " + input.name());
            if (input.size() <= 0) throw new IllegalArgumentException("The file " + input.name() + " is empty");
        }
        if (dataset == IngestionDataset.FOLDER) {
            for (IngestionDataset required : IngestionDataset.FOLDER_REQUIRED) {
                requireFile(names, required.getFileName());
            }
            requireFile(names, IngestionDataset.PROPERTY_OWNER_FILE);
        } else {
            requireFile(names, dataset.getFileName());
            if (dataset == IngestionDataset.OWNERS) requireFile(names, IngestionDataset.PROPERTY_OWNER_FILE);
        }
        for (InputSource input : inputs) {
            validateHeader(input, requiredHeaders(input.name()));
        }
    }

    private void requireFile(Set<String> names, String fileName) {
        if (!names.contains(fileName)) throw new IllegalArgumentException("Missing file " + fileName);
    }

    private List<String> requiredHeaders(String fileName) {
        if (IngestionDataset.PROPERTY_OWNER_FILE.equals(fileName)) return IngestionDataset.PROPERTY_OWNER_HEADERS;
        for (IngestionDataset dataset : IngestionDataset.values()) {
            if (fileName.equals(dataset.getFileName())) return dataset.getRequiredHeaders();
        }
        throw new IllegalArgumentException("Unexpected file " + fileName);
    }

    private void validateHeader(InputSource input, List<String> required) {
        Map<String, Integer> header;
        try (Reader reader = new java.io.InputStreamReader(input.open(), StandardCharsets.UTF_8);
                CSVParser parser = HEADER_FORMAT.parse(reader)) {
            header = parser.getHeaderMap();
        } catch (IOException | IllegalArgumentException unreadable) {
            throw new IllegalArgumentException("The file " + input.name() + " is not a readable CSV: " + unreadable.getMessage());
        }
        List<String> missing = required.stream().filter(column -> !header.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("The file " + input.name() + " is missing the columns " + missing);
        }
    }

    private void storeInputs(IngestionJob job, List<InputSource> inputs) {
        String location = inputStore.locationFor(job.getId());
        List<StoredInputRef> stored = new ArrayList<>();
        try {
            for (InputSource input : inputs) {
                try (InputStream content = input.open()) {
                    StoredInput copy = inputStore.store(location, input.name(), content, input.size());
                    stored.add(new StoredInputRef(input.name(), copy.size(), copy.etag()));
                }
            }
        } catch (IOException error) {
            inputStore.deleteAll(location);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The input files could not be stored");
        }
        job.setInputStorage(inputStore.storageType());
        job.setInputLocation(location);
        job.setInputScope(inputStore.scope());
        job.setParametersJson(json(new IngestionJobParameters(stored)));
        if ("LOCAL".equals(job.getInputStorage())) {
            // Nobody else can run it: give up if this instance does not claim it in time.
            job.setLeaseExpiresAt(Instant.now().plusSeconds(properties.getJobs().getQueueDeadlineSeconds()));
        }
    }

    private ImportJobDTO queue(IngestionJob job) {
        IngestionJob saved;
        try {
            saved = jobService.create(job);
        } catch (RuntimeException error) {
            if (job.getInputLocation() != null) inputStore.deleteAll(job.getInputLocation());
            throw error;
        }
        try {
            workerService.poll();
        } catch (RuntimeException error) {
            log.warn("Ingestion job {} queued; the poller will pick it up", saved.getId());
        }
        return mapper.toDTO(saved);
    }

    private void requireNoActiveJob(IngestionDataset dataset) {
        if (jobService.hasActive(dataset)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DATASET_JOB_ACTIVE
                + ": an ingestion job for " + dataset + " is already queued or running");
        }
    }

    /** The stored inputs must still be the ones the previous attempt read. */
    private void verifyInputs(IngestionJob previous) {
        if ("NONE".equals(previous.getInputStorage())) return;
        IngestionJobParameters parameters;
        try {
            parameters = objectMapper.readValue(previous.getParametersJson(), IngestionJobParameters.class);
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw refused(INPUT_UNAVAILABLE, previous);
        }
        for (StoredInputRef input : parameters.inputs()) {
            Optional<StoredInput> current = inputStore.describe(previous.getInputLocation(), input.name());
            if (current.isEmpty()) throw refused(INPUT_UNAVAILABLE, previous);
            if (current.get().size() != input.size() || !Objects.equals(current.get().etag(), input.etag())) {
                throw refused(INPUT_CHANGED, previous);
            }
        }
    }

    private ResponseStatusException refused(String code, IngestionJob job) {
        String explanation = switch (code) {
            case IngestionJobMapper.JOB_NOT_FINISHED -> "the job is still queued or running";
            case IngestionJobMapper.JOB_COMPLETED -> "the job completed; start a new import instead";
            case IngestionJobMapper.RETRY_ALREADY_ACTIVE -> "a retry of this job is already queued or running";
            case IngestionJobMapper.INPUT_ON_OTHER_INSTANCE -> "its input files are stored on instance "
                + job.getInputScope() + "; retry from that instance or start a new import";
            case INPUT_UNAVAILABLE -> "its input files are no longer available; start a new import";
            case INPUT_CHANGED -> "its input files changed since the job ran; start a new import";
            default -> "the job cannot be retried";
        };
        return new ResponseStatusException(HttpStatus.CONFLICT, code + ": " + explanation);
    }

    private Path stagedPath(String relativePath) {
        String staging = properties.getInput().getStagingDirectory();
        if (staging == null || staging.isBlank()) {
            throw new IllegalArgumentException("Staged inputs are disabled; upload the file instead");
        }
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("A staged name is required");
        Path base = Path.of(staging).toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base) || resolved.equals(base)) {
            throw new IllegalArgumentException("Staged names must stay inside the staging directory");
        }
        return resolved;
    }

    private InputSource pathSource(Path file, String name) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return new InputSource() {
            @Override public String name() { return name; }
            @Override public long size() { return size; }
            @Override public InputStream open() throws IOException { return Files.newInputStream(file); }
        };
    }

    private static Set<String> folderFileNames() {
        Set<String> names = new HashSet<>();
        for (IngestionDataset dataset : IngestionDataset.FOLDER_ORDER) names.add(dataset.getFileName());
        names.add(IngestionDataset.PROPERTY_OWNER_FILE);
        return names;
    }

    private String json(IngestionJobParameters parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException(invalid);
        }
    }
}
