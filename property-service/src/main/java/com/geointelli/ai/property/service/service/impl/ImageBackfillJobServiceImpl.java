package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;
import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.entity.ImageBackfillJob;
import com.geointelli.ai.property.service.entity.ImageBackfillResult;
import com.geointelli.ai.property.service.entity.ImageBackfillResultId;
import com.geointelli.ai.property.service.mapper.ImageBackfillJobMapper;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.ImageBackfillJobRepository;
import com.geointelli.ai.property.service.repository.ImageBackfillClaimRepository;
import com.geointelli.ai.property.service.repository.ImageBackfillResultRepository;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.ImageBackfillJobService;
import lombok.RequiredArgsConstructor;

/** Uses JPA transactions to persist jobs, results and progress together. */
@Service
@RequiredArgsConstructor
@Transactional
public class ImageBackfillJobServiceImpl implements ImageBackfillJobService {
    private final ImageBackfillJobRepository jobs;
    private final ImageBackfillResultRepository results;
    private final CountyRepository counties;
    private final PropertyRepository properties;
    private final PropertyImageRepository images;
    private final ImageBackfillJobMapper mapper;
    private final ImageBackfillClaimRepository claims;

    @Override
    public Long create(ImageBackfillRequest request) {
        jobs.interruptExpired();
        ImageBackfillJob job = new ImageBackfillJob();
        job.setCounty(counties.getReferenceById(request.getCountyId()));
        job.setStatus("QUEUED");
        job.setBatchSize(request.getBatchSize());
        job.setAfterPropertyId(request.getAfterPropertyId());
        job.setLastPropertyId(request.getAfterPropertyId());
        return jobs.saveAndFlush(job).getId();
    }

    @Override
    public void running(Long id, int total) {
        ImageBackfillJob job = getForUpdate(id);
        if (!active(job)) throw new IllegalStateException("Job is no longer active");
        job.setStatus("RUNNING");
        job.setTotal(total);
        jobs.save(job);
    }

    @Override
    public void record(Long jobId, ImageBackfillResultDTO outcome) {
        ImageBackfillJob job = getForUpdate(jobId);
        if (!active(job)) return;
        ImageBackfillResultId key = new ImageBackfillResultId(jobId, outcome.getPropertyId());
        if (results.existsById(key)) return;

        ImageBackfillResult result = new ImageBackfillResult();
        result.setJob(job);
        result.setProperty(properties.getReferenceById(outcome.getPropertyId()));
        result.setStatus(outcome.getStatus());
        result.setReason(outcome.getReason());
        if (outcome.getImageId() != null) result.setImage(images.getReferenceById(outcome.getImageId()));
        results.save(result);

        job.setProcessed(job.getProcessed() + 1);
        if ("COMPLETED".equals(outcome.getStatus())) job.setCompleted(job.getCompleted() + 1);
        if ("SKIPPED".equals(outcome.getStatus())) job.setSkipped(job.getSkipped() + 1);
        if ("FAILED".equals(outcome.getStatus())) job.setFailed(job.getFailed() + 1);
        job.setLastPropertyId(Math.max(job.getLastPropertyId(), outcome.getPropertyId()));
        jobs.save(job);
        claims.complete(jobId, outcome.getPropertyId());
    }

    @Override
    public void finish(Long id, String status, String errorCode) {
        ImageBackfillJob job = getForUpdate(id);
        if (!active(job)) return;
        job.setStatus(status);
        job.setErrorCode(errorCode);
        job.setFinishedAt(Instant.now());
        jobs.save(job);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ImageBackfillJobDTO> find(Long id) {
        return jobs.findById(id).map(job -> {
            ImageBackfillJobDTO dto = mapper.toDTO(job);
            dto.setResults(results.findByJob_IdOrderByProperty_IdAsc(id).stream().map(mapper::toDTO).toList());
            return dto;
        });
    }

    private ImageBackfillJob getForUpdate(Long id) {
        return jobs.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Image backfill job not found"));
    }

    private boolean active(ImageBackfillJob job) {
        return "QUEUED".equals(job.getStatus()) || "RUNNING".equals(job.getStatus());
    }
}
