package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.util.List;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityReviewStatus;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Review state of one data-quality finding: what it is, where it stands now, and every action so far (SCRUM-190).
 * Returned by GET and PATCH on /api/admin/data-quality/issues/{reference}. The finding itself is not re-detected
 * here; the scanner report of SCRUM-189 says whether it is still reported.
 *
 * @param reference        reproducible reference RULE:entityType:entityId
 * @param rule             rule that reports the finding
 * @param category         kind of issue of that rule
 * @param entityType       PROPERTY, SALE or ASSESSMENT
 * @param entityId         ID of that entity
 * @param propertyId       property concerned, null when the sale or assessment has none
 * @param status           current status; OPEN when never reviewed or reopened
 * @param reviewedByUserId admin of the latest action, null when OPEN and never reviewed
 * @param reviewedAt       time of the latest action, null when never reviewed
 * @param reason           reason of the latest action, null when never reviewed or not given
 * @param history          every action, oldest first; empty when never reviewed
 */
public record DataQualityIssueReviewDTO(
        @Schema(example = "SALE_DUPLICATE:SALE:9012") String reference,
        DataQualityRule rule,
        DataQualityCategory category,
        @Schema(example = "SALE") String entityType,
        @Schema(example = "9012") Long entityId,
        @Schema(example = "345") Long propertyId,
        @Schema(example = "RESOLVED") DataQualityReviewStatus status,
        @Schema(example = "3") Long reviewedByUserId,
        Instant reviewedAt,
        @Schema(example = "Corrected in the November re-import") String reason,
        List<DataQualityReviewEventDTO> history) {
}
