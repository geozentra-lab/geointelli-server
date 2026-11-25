package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import com.geointelli.ai.property.service.enums.DataQualityReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One review action on a data-quality finding, as stored in the append-only log (SCRUM-190).
 *
 * @param id               row ID, increasing with time
 * @param fromStatus       status before the action
 * @param toStatus         status after the action
 * @param reason           reason given by the reviewer; null only when toStatus is IN_REVIEW
 * @param reviewedByUserId users.id of the admin who acted
 * @param reviewedAt       when (UTC)
 */
public record DataQualityReviewEventDTO(
        @Schema(example = "17") Long id,
        @Schema(example = "IN_REVIEW") DataQualityReviewStatus fromStatus,
        @Schema(example = "RESOLVED") DataQualityReviewStatus toStatus,
        @Schema(example = "Corrected in the November re-import") String reason,
        @Schema(example = "3") Long reviewedByUserId,
        @Schema(example = "2026-11-17T09:12:45.123456Z") Instant reviewedAt) {
}
