package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Portfolio metadata; holdings are paginated separately through the holdings endpoint. */
public record PortfolioDTO(Long id, String name, String description, String currency, long holdingCount,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {
}
