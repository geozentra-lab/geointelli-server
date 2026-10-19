package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Defines a bounded batch of properties to process for one county. */
@Data
public class ImageBackfillRequest {
    @NotNull
    @Min(1)
    private Long countyId;

    @Min(1)
    @Max(100)
    private int batchSize = 20;

    @Min(0)
    private long afterPropertyId = 0;
}
