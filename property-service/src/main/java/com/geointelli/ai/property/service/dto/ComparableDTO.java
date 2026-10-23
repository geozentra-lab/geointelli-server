package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/** A recorded sale with its position, rule-based similarity score and explanation. */
@Data
public class ComparableDTO {
    private int rank;
    private ComparableCandidateDTO candidate;
    private BigDecimal similarityScore;
    private int evidenceCoveragePercent;
    private ComparableScoreBreakdownDTO scoreBreakdown;
    private List<String> selectionReasons;
    private List<String> missingData;
}
