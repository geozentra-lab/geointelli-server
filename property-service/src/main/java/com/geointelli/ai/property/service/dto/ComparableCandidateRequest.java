package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Controls the bounded search for recent, qualified sale candidates. */
@Data
public class ComparableCandidateRequest {
    @Min(1) @Max(120)
    private int months = 24;
    @Min(100) @Max(10000)
    private int radiusMeters = 2000;
    @Min(0) @Max(100)
    private int areaTolerancePercent = 25;
    @Min(0) @Max(5)
    private int bedroomTolerance = 1;
    @Min(3) @Max(100)
    private int limit = 20;
}
