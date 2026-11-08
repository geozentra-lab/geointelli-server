package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Data-quality scan result: the summary of every rule, plus one page of issues when a rule is requested (SCRUM-189). */
@Data
public class DataQualityReportDTO {
    @Schema(description = "When the scan ran (UTC); issues are computed on each request")
    private Instant generatedAt;
    @Schema(description = "Every rule with its number of issues, always returned")
    private List<DataQualityRuleSummaryDTO> summary;
    @Schema(description = "Rule whose issues are listed; absent when no rule is requested", example = "SALE_DUPLICATE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DataQualityRule rule;
    @Schema(description = "One page of issues of the requested rule; absent when no rule is requested")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PageResponse<DataQualityIssueDTO> issues;
    @Schema(description = "How the scan works", example = "Issues are computed on each request; nothing is changed or deleted.")
    private String note;
}
