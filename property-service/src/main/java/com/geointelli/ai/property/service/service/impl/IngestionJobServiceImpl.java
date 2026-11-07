package com.geointelli.ai.property.service.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.dto.IngestionJobItemDTO;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.entity.IngestionJobItem;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.repository.IngestionJobItemRepository;
import com.geointelli.ai.property.service.repository.IngestionJobRepository;
import com.geointelli.ai.property.service.service.IngestionJobService;
import lombok.RequiredArgsConstructor;

/** JPA-backed job persistence; every method runs in the caller's transaction or a short one of its own. */
@Service
@RequiredArgsConstructor
@Transactional
public class IngestionJobServiceImpl implements IngestionJobService {
    private static final int CLAIM_CANDIDATES = 5;

    private final IngestionJobRepository jobs;
    private final IngestionJobItemRepository items;
    private final IngestionProperties properties;

    @Override
    public IngestionJob create(IngestionJob job) {
        if (!IngestionJobStatus.QUEUED.equals(job.getStatus())) {
            throw new IllegalArgumentException("A new job must be QUEUED");
        }
        if (job.getId() == null) job.setId(UUID.randomUUID());
        return jobs.saveAndFlush(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngestionJob> find(UUID id) {
        return jobs.findById(id);
    }

    @Override
    public Optional<UUID> claimNext(String workerId, String scope, Duration lease) {
        jobs.interruptExpired();
        for (String candidate : jobs.findQueuedIds(scope, CLAIM_CANDIDATES)) {
            UUID id = UUID.fromString(candidate);
            if (jobs.claim(id, workerId, Instant.now().plus(lease)) == 1) return Optional.of(id);
        }
        return Optional.empty();
    }

    @Override
    public boolean renewLease(UUID id, String workerId, Duration lease) {
        return jobs.renewLease(id, workerId, Instant.now().plus(lease)) == 1;
    }

    @Override
    public int recordProgress(UUID id, String workerId, Counters counters, String step, String checkpointJson,
            Duration lease) {
        return jobs.recordProgress(id, workerId, counters.total(), counters.processed(), counters.succeeded(),
            counters.skipped(), counters.failed(), step, checkpointJson, Instant.now().plus(lease));
    }

    @Override
    public void finish(UUID id, String workerId, String status, String errorCode, String errorMessage) {
        jobs.finish(id, workerId, status, errorCode, errorMessage);
    }

    @Override
    public void addItem(UUID id, String itemKey, String status, String reason, String message) {
        if (items.countByJobId(id) >= properties.getJobs().getMaxItemsPerJob()) return;
        IngestionJobItem item = new IngestionJobItem();
        item.setJobId(id);
        item.setItemKey(itemKey);
        item.setStatus(status);
        item.setReason(reason);
        item.setMessage(message);
        items.save(item);
    }

    @Override
    public int interruptExpired() {
        return jobs.interruptExpired();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActive(IngestionDataset dataset) {
        return jobs.existsByDatasetAndStatusIn(dataset, IngestionJobStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveRetry(UUID jobId) {
        return jobs.existsByRetryOfJobIdAndStatusIn(jobId, IngestionJobStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionJobItemDTO> items(UUID id, int limit) {
        return items.findByJobIdOrderByIdAsc(id, PageRequest.of(0, limit)).stream()
            .map(item -> new IngestionJobItemDTO(item.getItemKey(), item.getStatus(), item.getReason(), item.getMessage()))
            .toList();
    }
}
