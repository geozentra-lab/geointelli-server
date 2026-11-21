package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.enums.DataQualityReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/admin/data-quality/issues/{reference}: the status the admin moves the finding to, with the
 * reason (SCRUM-190). The reviewer and the time come from the bearer token and the clock, never from the body.
 *
 * @param status target status; must be a supported transition from the current one
 * @param reason why; required for RESOLVED, IGNORED and OPEN (reopen), optional for IN_REVIEW, max 500 characters
 */
public record DataQualityReviewRequest(
        @Schema(description = "Target status: IN_REVIEW, RESOLVED, IGNORED or OPEN (reopen)", example = "RESOLVED")
        @NotNull(message = "status is required")
        DataQualityReviewStatus status,
        @Schema(description = "Why the finding is moved; required except for IN_REVIEW", example = "Corrected in the November re-import")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason) {
}
