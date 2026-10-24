package com.geointelli.ai.property.service.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pagination parameters for list endpoints: page defaults to 0, size to 20 (max 100).
 */
@Data
public class PageQuery {
    @Min(0)
    private int page = 0;

    @Min(1) @Max(100)
    private int size = 20;
}
