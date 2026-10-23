package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** Ranked sales within the disclosed candidate pool, not an appraisal or value estimate. */
@Data
public class ComparableResponse {
    private Long propertyId;
    private ComparableSubjectDTO subject;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate asOfDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate saleDateFrom;
    private ComparableCandidateRequest search;
    private String selectionVersion;
    private String rankingVersion;
    private Map<String, Integer> scoringWeights;
    private int candidatePoolLimit;
    private int consideredCandidates;
    private boolean candidatePoolLimitReached;
    private int minimumCandidates;
    private int returnedCount;
    private String status;
    private List<String> warnings;
    private List<ComparableDTO> comparables;
}
