package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.mapper.IngestionJobMapper;
import com.geointelli.ai.property.service.repository.IngestionJobRepository;
import com.geointelli.ai.property.service.repository.IngestionJobRepository.ImportJobRow;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import com.geointelli.ai.property.service.service.ImageBackfillClaimService;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.PropertyImageBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Merges the Mapillary backfill jobs and the ingestion jobs into one admin history. Numeric IDs address
 * backfill jobs, UUIDs address ingestion jobs, so the two never collide.
 */
@Service
@RequiredArgsConstructor
public class AdminImportJobServiceImpl implements AdminImportJobService {

    static final int MAX_PAGE_SIZE = 100;
    static final String NOTE = "MAPILLARY_IMAGE_BACKFILL jobs have numeric IDs; CSV_IMPORT and PROPERTY_INGESTION "
            + "jobs have UUIDs and can be retried. PROPERTY_IMAGE_CSV_IMPORT runs without a job record.";
    private static final Pattern NUMERIC = Pattern.compile("\\d{1,18}");

    private final ImageBackfillClaimService imageBackfillClaimService;
    private final PropertyImageBackfillService propertyImageBackfillService;
    private final IngestionJobService ingestionJobService;
    private final IngestionJobRepository ingestionJobs;
    private final IngestionJobMapper ingestionJobMapper;
    private final IngestionLaunchService ingestionLaunchService;

    @Override
    @Transactional
    public ImportJobPageDTO listJobs(int page, int size, ImportJobType type, String status) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        // page * size is the row offset; past Integer.MAX_VALUE Spring Data fails with a 500
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page is too large for size " + size);
        }
        if (status != null && !IngestionJobStatus.ALL.contains(status)) {
            throw new IllegalArgumentException("status must be one of " + IngestionJobStatus.ALL);
        }
        if (type != null && !type.isTracked()) {
            throw new IllegalArgumentException(type + " jobs are not tracked");
        }

        // Jobs whose worker died are shown as INTERRUPTED, not RUNNING.
        imageBackfillClaimService.interruptExpired();
        ingestionJobService.interruptExpired();

        String typeName = type == null ? null : type.name();
        List<ImportJobDTO> content = ingestionJobs.listAllJobs(typeName, status, size, (long) page * size).stream()
                .map(ImportJobRow::from).map(ingestionJobMapper::toDTO).toList();
        long total = ingestionJobs.countAllJobs(typeName, status);
        long totalPages = (total + size - 1) / size;

        ImportJobPageDTO result = new ImportJobPageDTO();
        result.setJobs(new PageResponse<>(content, page, size, total, totalPages, page == 0,
                (long) page + 1 >= totalPages));
        result.setUntrackedJobTypes(Arrays.stream(ImportJobType.values())
                .filter(candidate -> !candidate.isTracked())
                .toList());
        result.setNote(NOTE);
        return result;
    }

    @Override
    public ImportJobDTO getJob(String id) {
        if (id != null && NUMERIC.matcher(id).matches()) {
            long backfillId = Long.parseLong(id);
            if (backfillId <= 0) throw new IllegalArgumentException("id must be a positive number");
            // Reuses the existing lookup: it interrupts expired jobs and returns 404 for an unknown ID.
            ImageBackfillJobDTO job = propertyImageBackfillService.get(backfillId);
            ImportJobDTO importJob = toImportJob(job);
            importJob.setResults(job.getResults());
            return importJob;
        }
        return ingestionLaunchService.get(ingestionJobId(id));
    }

    @Override
    public ImportJobDTO retryJob(String id, String requestedBy) {
        if (id != null && NUMERIC.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, IngestionJobMapper.RETRY_NOT_SUPPORTED
                    + ": Mapillary backfill jobs cannot be retried; start a new backfill instead");
        }
        return ingestionLaunchService.retry(ingestionJobId(id), requestedBy);
    }

    private UUID ingestionJobId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException("id must be a numeric backfill job ID or an ingestion job UUID");
        }
    }

    private ImportJobDTO toImportJob(ImageBackfillJobDTO source) {
        ImportJobDTO job = new ImportJobDTO();
        job.setId(String.valueOf(source.getJobId()));
        job.setJobType(ImportJobType.MAPILLARY_IMAGE_BACKFILL);
        job.setJobId(source.getJobId());
        job.setCountyId(source.getCountyId());
        job.setStatus(source.getStatus());
        job.setTotal(source.getTotal());
        job.setProcessed(source.getProcessed());
        job.setCompleted(source.getCompleted());
        job.setSkipped(source.getSkipped());
        job.setFailed(source.getFailed());
        job.setErrorCode(source.getErrorCode());
        job.setCreatedAt(source.getCreatedAt());
        job.setFinishedAt(source.getFinishedAt());
        job.setRetryable(false);
        job.setRetryBlockedReason(IngestionJobMapper.RETRY_NOT_SUPPORTED);
        return job;
    }
}
