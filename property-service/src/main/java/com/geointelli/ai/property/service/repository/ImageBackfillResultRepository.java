package com.geointelli.ai.property.service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.ImageBackfillResult;
import com.geointelli.ai.property.service.entity.ImageBackfillResultId;

/** Persists individual property results using JPA. */
public interface ImageBackfillResultRepository extends JpaRepository<ImageBackfillResult, ImageBackfillResultId> {
    List<ImageBackfillResult> findByJob_IdOrderByProperty_IdAsc(Long jobId);
}
