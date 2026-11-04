package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.util.Map;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;

/** Every supported stage is present, including zero-count stages. */
public record CrmPipelineDTO(long total, Map<CrmTransactionStage, Long> stages, Instant asOf) {
}
