package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import com.geointelli.ai.property.service.service.ImageBackfillClaimService;
import com.geointelli.ai.property.service.service.ImageBackfillJobService;
import com.geointelli.ai.property.service.service.PropertyImageBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/** Reuses the persisted Mapillary backfill jobs as the admin import-job history. */
@Service
@RequiredArgsConstructor
public class AdminImportJobServiceImpl implements AdminImportJobService {

    static final int MAX_PAGE_SIZE = 100;
    static final String NOTE = "Only MAPILLARY_IMAGE_BACKFILL jobs are persisted; "
            + "CSV_IMPORT and PROPERTY_INGESTION run without a job record.";

    private final ImageBackfillJobService imageBackfillJobService;
    private final ImageBackfillClaimService imageBackfillClaimService;
    private final PropertyImageBackfillService propertyImageBackfillService;

    @Override
    public ImportJobPageDTO listJobs(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Same as the existing detail endpoint: jobs whose worker died are shown as INTERRUPTED, not RUNNING.
        imageBackfillClaimService.interruptExpired();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        ImportJobPageDTO result = new ImportJobPageDTO();
        result.setJobs(PageResponse.from(imageBackfillJobService.list(pageable).map(this::toImportJob)));
        result.setUntrackedJobTypes(Arrays.stream(ImportJobType.values())
                .filter(type -> !type.isTracked())
                .toList());
        result.setNote(NOTE);
        return result;
    }

    @Override
    public ImportJobDTO getJob(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }

        // Reuses the existing lookup: it interrupts expired jobs and returns 404 for an unknown ID.
        ImageBackfillJobDTO job = propertyImageBackfillService.get(id);

        ImportJobDTO importJob = toImportJob(job);
        importJob.setResults(job.getResults());
        return importJob;
    }

    private ImportJobDTO toImportJob(ImageBackfillJobDTO source) {
        ImportJobDTO job = new ImportJobDTO();
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
        return job;
    }
}
