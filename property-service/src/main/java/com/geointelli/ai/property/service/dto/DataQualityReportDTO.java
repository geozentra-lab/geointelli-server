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
    @Schema(description = "When the returned rule counts were calculated (UTC); null before the first count ends",
            nullable = true)
    private Instant generatedAt;
    @Schema(description = "True while the rule counts are being recalculated in the background; the counts returned "
            + "meanwhile are the previous ones, or none before the first count ends (SCRUM-206)")
    private boolean calculating;
    @Schema(description = "Every rule with its number of issues, always returned")
    private List<DataQualityRuleSummaryDTO> summary;
    @Schema(description = "Rule whose issues are listed; absent when no rule is requested", example = "SALE_DUPLICATE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DataQualityRule rule;
    @Schema(description = "One page of issues of the requested rule; absent when no rule is requested. Without a "
            + "count for the rule, totalElements is the lowest total the pages read so far prove, and last says "
            + "whether a next page exists")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PageResponse<DataQualityIssueDTO> issues;
    @Schema(description = "How fresh the counts and the issue page are", example = "Rule counts were calculated at "
            + "generatedAt and are reused for up to 10 minutes, then recalculated in the background. Before the "
            + "first count, issueCount is null and calculating is true. The issue page is read on each request. "
            + "Nothing is changed or deleted.")
    private String note;
}
