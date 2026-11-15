package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.entity.LenderLoanType;

/** Persisted application including source identity and recorded approval/funding evidence. */
public record LenderApplicationDTO(Long id, Long organizationId, Long createdByUserId, String applicantName,
                                   String applicantEmail, Long propertyId, BigDecimal requestedAmount, String currency, LenderApplicationStage stage,
                                   LenderLoanType loanType,
                                   BigDecimal approvedAmount, BigDecimal fundedAmount, Instant approvedAt, Instant fundedAt,
                                   String notes, String source, String externalId, LocalDateTime createdAt, LocalDateTime updatedAt) {}

