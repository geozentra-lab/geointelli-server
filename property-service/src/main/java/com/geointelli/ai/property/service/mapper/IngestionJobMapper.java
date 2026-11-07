package com.geointelli.ai.property.service.mapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.repository.IngestionJobRepository.ImportJobRow;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobService;
import lombok.RequiredArgsConstructor;

/**
 * Maps ingestion jobs, and the merged admin listing rows, to {@link ImportJobDTO}. Retry eligibility here
 * uses database facts only; the input store is consulted when a retry is actually requested.
 */
@Component
@RequiredArgsConstructor
public class IngestionJobMapper {
    public static final String JOB_NOT_FINISHED = "JOB_NOT_FINISHED";
    public static final String JOB_COMPLETED = "JOB_COMPLETED";
    public static final String RETRY_ALREADY_ACTIVE = "RETRY_ALREADY_ACTIVE";
    public static final String INPUT_ON_OTHER_INSTANCE = "INPUT_ON_OTHER_INSTANCE";
    public static final String RETRY_NOT_SUPPORTED = "RETRY_NOT_SUPPORTED";

    private final IngestionJobService jobService;
    private final IngestionInputStore inputStore;

    public ImportJobDTO toDTO(IngestionJob job) {
        ImportJobDTO dto = new ImportJobDTO();
        dto.setId(job.getId().toString());
        dto.setJobType(job.getJobType());
        dto.setDataset(job.getDataset());
        dto.setCountyId(job.getCounty() == null ? null : job.getCounty().getId());
        dto.setSourceCounty(job.getSourceCounty());
        dto.setSourceState(job.getSourceState());
        dto.setStatus(job.getStatus());
        dto.setTotal(job.getTotal());
        dto.setProcessed(job.getProcessed());
        dto.setCompleted(job.getSucceeded());
        dto.setSkipped(job.getSkipped());
        dto.setFailed(job.getFailed());
        dto.setCurrentStep(job.getCurrentStep());
        dto.setErrorCode(job.getErrorCode());
        dto.setErrorMessage(job.getErrorMessage());
        dto.setInputStorage(job.getInputStorage());
        dto.setAttempt(job.getAttempt());
        dto.setRetryOfJobId(job.getRetryOfJobId() == null ? null : job.getRetryOfJobId().toString());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setStartedAt(job.getStartedAt());
        dto.setFinishedAt(job.getFinishedAt());
        String blocked = retryBlockedReason(job.getStatus(), jobService.hasActiveRetry(job.getId()),
            job.getInputStorage(), job.getInputScope());
        dto.setRetryable(blocked == null);
        dto.setRetryBlockedReason(blocked);
        return dto;
    }

    /** Maps one row of the merged listing; backfill rows keep their numeric {@code jobId}. */
    public ImportJobDTO toDTO(ImportJobRow row) {
        ImportJobDTO dto = new ImportJobDTO();
        ImportJobType type = ImportJobType.valueOf(row.jobType());
        dto.setId(row.id());
        dto.setJobType(type);
        dto.setDataset(row.dataset() == null ? null : IngestionDataset.valueOf(row.dataset()));
        dto.setCountyId(row.countyId());
        dto.setSourceCounty(row.sourceCounty());
        dto.setSourceState(row.sourceState());
        dto.setStatus(row.status());
        dto.setTotal(row.total());
        dto.setProcessed(row.processed());
        dto.setCompleted(row.succeeded());
        dto.setSkipped(row.skipped());
        dto.setFailed(row.failed());
        dto.setCurrentStep(row.currentStep());
        dto.setErrorCode(row.errorCode());
        dto.setErrorMessage(row.errorMessage());
        dto.setInputStorage(row.inputStorage());
        dto.setAttempt(row.attempt());
        dto.setRetryOfJobId(row.retryOfJobId());
        dto.setCreatedAt(toInstant(row.createdAt()));
        dto.setStartedAt(toInstant(row.startedAt()));
        dto.setFinishedAt(toInstant(row.finishedAt()));
        if (type == ImportJobType.MAPILLARY_IMAGE_BACKFILL) {
            dto.setJobId(Long.parseLong(row.id()));
            dto.setRetryable(false);
            dto.setRetryBlockedReason(RETRY_NOT_SUPPORTED);
        } else {
            String blocked = retryBlockedReason(row.status(), row.hasActiveRetry(), row.inputStorage(), row.inputScope());
            dto.setRetryable(blocked == null);
            dto.setRetryBlockedReason(blocked);
        }
        return dto;
    }

    /** Native queries return timestamps as the driver's type; the DTO always carries an Instant. */
    static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        throw new IllegalArgumentException("Unsupported timestamp " + value.getClass());
    }

    /** Null when a retry may be requested, otherwise the reason code it would be refused with. */
    public String retryBlockedReason(String status, boolean hasActiveRetry, String inputStorage, String inputScope) {
        if (IngestionJobStatus.ACTIVE.contains(status)) return JOB_NOT_FINISHED;
        if (IngestionJobStatus.COMPLETED.equals(status)) return JOB_COMPLETED;
        if (hasActiveRetry) return RETRY_ALREADY_ACTIVE;
        if ("LOCAL".equals(inputStorage) && !Objects.equals(inputScope, inputStore.scope())) {
            return INPUT_ON_OTHER_INSTANCE;
        }
        return null;
    }
}
