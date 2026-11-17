package com.geointelli.ai.property.service.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Review status of a data-quality finding (SCRUM-190). A finding the scanner reports starts as {@link #OPEN}
 * without any stored row; every later status is the newest row of {@code data_quality_issue_reviews}.
 *
 * <p>Supported transitions:
 * <ul>
 *   <li>OPEN to IN_REVIEW, RESOLVED or IGNORED;</li>
 *   <li>IN_REVIEW to RESOLVED, IGNORED, or back to OPEN (released without a decision);</li>
 *   <li>RESOLVED or IGNORED back to OPEN (reopened), from where the review starts again.</li>
 * </ul>
 * Moving to the same status, or any other move, is refused.
 */
public enum DataQualityReviewStatus {
    /** Reported by the scanner and not decided; also the state of a reopened finding. */
    OPEN,
    /** An administrator is looking at it. */
    IN_REVIEW,
    /** The underlying data was corrected by a separately authorised change; the finding should disappear. */
    RESOLVED,
    /** Reviewed and accepted as is (for example a false positive); the finding keeps being reported. */
    IGNORED;

    /** Where a finding in this status may move. */
    public Set<DataQualityReviewStatus> allowedTargets() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_REVIEW, RESOLVED, IGNORED);
            case IN_REVIEW -> EnumSet.of(RESOLVED, IGNORED, OPEN);
            case RESOLVED, IGNORED -> EnumSet.of(OPEN);
        };
    }

    /** True when a finding in this status may move to {@code target}. */
    public boolean canMoveTo(DataQualityReviewStatus target) {
        return target != null && allowedTargets().contains(target);
    }

    /** True when moving to this status needs a reason: everything except taking a finding into review. */
    public boolean requiresReason() {
        return this != IN_REVIEW;
    }
}
