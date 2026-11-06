package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;

/** All-time current-status totals in USD; approved includes APPROVED and FUNDED, funded includes FUNDED only. */
public record LenderPipelineDTO(Long organizationId, String currency, long total,
        Map<LenderApplicationStage, StageTotals> stages, long approvedCount, BigDecimal approvedAmount,
        long fundedCount, BigDecimal fundedAmount, Instant asOf, String scope) {
    public record StageTotals(long count, BigDecimal requestedAmount) {}
}

