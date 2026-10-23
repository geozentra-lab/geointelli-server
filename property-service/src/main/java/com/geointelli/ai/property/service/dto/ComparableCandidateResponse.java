package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

/** Bounded retrieval results; these are not ranked or adjusted valuations. */
@Data
@Builder
public class ComparableCandidateResponse {
    private Long propertyId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate asOfDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate saleDateFrom;
    private int radiusMeters;
    private int areaTolerancePercent;
    private int bedroomTolerance;
    private int limit;
    private int minimumCandidates;
    private int returnedCount;
    private String status;
    private String selectionVersion;
    private List<String> warnings;
    private List<ComparableCandidateDTO> candidates;
}
