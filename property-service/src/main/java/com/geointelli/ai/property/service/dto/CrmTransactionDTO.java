package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;

/** A manual deal record, not a payment or legal settlement instruction. */
public record CrmTransactionDTO(Long id, String title, String description, CrmTransactionStage stage,
        Long clientId, Long leadId, Long propertyId, LocalDate expectedCloseDate, Instant closedAt,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
