package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.LeadStage;

/** An enquiry, its retained source and its current stage. */
public record CrmLeadDTO(Long id, Long clientId, Long propertyId, String title, String source,
        LeadStage stage, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
