package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;

/** Historical subject IDs are retained even when a client is deleted. */
public record CrmActivityDTO(Long id, Long clientId, CrmSubjectType subjectType, Long subjectId,
        CrmActivityType activityType, String description, Instant createdAt) {
}
