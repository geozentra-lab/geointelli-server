package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;

/** Complete partner snapshots. Identity is organization + source + externalId; row order is significant. */
public record LenderApplicationImportRequest(String source, List<Row> rows) {
    public record Row(String externalId, String applicantName, String applicantEmail, Long propertyId,
        BigDecimal requestedAmount, String currency, LenderApplicationStage stage, BigDecimal approvedAmount,
        BigDecimal fundedAmount, Instant approvedAt, Instant fundedAt, String notes) {}
}

