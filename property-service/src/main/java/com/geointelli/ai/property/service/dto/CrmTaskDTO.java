package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;

/** Due/completion times are UTC instants; overdue is calculated at response time. */
public record CrmTaskDTO(Long id, String title, String description, CrmTaskStatus status,
        Instant dueAt, Instant completedAt, boolean overdue, Long clientId, Long leadId,
        Long propertyId, Long transactionId, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
