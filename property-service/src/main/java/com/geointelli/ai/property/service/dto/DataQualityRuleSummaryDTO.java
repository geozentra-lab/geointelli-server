package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Number of issues found by one data-quality rule, with the rule definition (SCRUM-189). */
@Data
public class DataQualityRuleSummaryDTO {
    @Schema(description = "Rule code", example = "ASSESSMENT_STALE")
    private DataQualityRule rule;
    @Schema(description = "Kind of issue the rule detects", example = "STALE")
    private DataQualityCategory category;
    @Schema(description = "Entity reported by the rule: PROPERTY, SALE or ASSESSMENT", example = "PROPERTY")
    private String entityType;
    @Schema(description = "Table(s) the rule reads", example = "assessments")
    private String source;
    @Schema(description = "What the rule checks", example = "Latest assessment is older than 2 years")
    private String description;
    @Schema(description = "Number of issues found by the rule; null when there is no count yet or the rule could "
            + "not be counted (see unavailableReason), never a guessed 0", example = "12", nullable = true)
    private Long issueCount;
    @Schema(description = "Why issueCount is null; absent when the rule has a count",
            example = "Counting took longer than 120 seconds and was stopped")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String unavailableReason;
}
