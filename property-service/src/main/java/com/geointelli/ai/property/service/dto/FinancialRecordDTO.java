package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Amounts are actual USD cash totals, not projected income or ownership-adjusted calculations. */
@Schema(description = "User-reported actual transaction. MORTGAGE_PAYMENT is cash paid, including any principal; "
    + "it must not be treated entirely as an operating expense in future NOI/profit calculations. "
    + "PATCH overwrites supplied facts and updates updatedAt; no historical revision ledger is provided.")
public record FinancialRecordDTO(Long id, Long portfolioId, Long holdingId, FinancialRecordCategory category,
    FinancialRecordType type, BigDecimal amount, String currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate transactionDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate periodStart,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate periodEnd,
    String description, String source,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {
}
