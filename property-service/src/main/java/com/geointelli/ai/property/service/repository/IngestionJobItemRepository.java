package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.IngestionJobItem;

/** Persists the failed and skipped records of ingestion jobs. */
public interface IngestionJobItemRepository extends JpaRepository<IngestionJobItem, Long> {
    List<IngestionJobItem> findByJobIdOrderByIdAsc(UUID jobId, Pageable pageable);

    long countByJobId(UUID jobId);
}
