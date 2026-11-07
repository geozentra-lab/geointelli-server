package com.geointelli.ai.property.service.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.service.IngestionJobWorkerService;
import lombok.RequiredArgsConstructor;

/**
 * Polls for queued ingestion jobs so every instance can pick up work created on another one, and so a
 * job queued while this instance was busy is not forgotten. Disabled with {@code ingestion.jobs.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "ingestion.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class IngestionJobScheduler {
    private final IngestionJobWorkerService workerService;

    @Scheduled(fixedDelayString = "${ingestion.jobs.poll-delay-millis:5000}", initialDelayString = "${ingestion.jobs.poll-delay-millis:5000}")
    public void poll() {
        workerService.poll();
    }
}
