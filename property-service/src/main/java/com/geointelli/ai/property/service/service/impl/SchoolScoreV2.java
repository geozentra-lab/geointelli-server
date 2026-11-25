package com.geointelli.ai.property.service.service.impl;

/**
 * School score v2 (SCRUM-203): 0-100, the weighted average of three parts, each the weighted average of the three
 * levels (elementary 0.40, middle 0.30, high 0.30). {@link WeightedScore#average} skips an unknown part or level and
 * rescales the other weights, so a missing value never counts as 0.
 * <ul>
 *   <li>quality, weight 0.60: the FLDOE statewide percentile of the assigned school; unknown when the school has no
 *       grade (I, N, P or not listed);</li>
 *   <li>access, weight 0.25: the distance to the assigned school, 100 within 1 mile, 75 within 2, 50 within 5, 25
 *       within 10, else 0;</li>
 *   <li>choice, weight 0.15: the best statewide percentile among the other schools within 3 miles
 *       ({@link SchoolChoiceIndex}); 0 when there is no other school, unknown when none of them is graded.</li>
 * </ul>
 * A level whose zone's school is not in the school data (UNMATCHED_ZONE) has no quality and no access value: it is
 * left out, never replaced by another school. A property without any assigned school has no score (the choice part
 * alone is not a school score).
 */
final class SchoolScoreV2 {

    static final int VERSION = 2;
    /** Quality, access, choice. */
    static final double[] PART_WEIGHTS = {0.60, 0.25, 0.15};
    /** Elementary, middle, high. */
    static final double[] LEVEL_WEIGHTS = {0.40, 0.30, 0.30};
    static final double MILE_M = 1609.344;

    private SchoolScoreV2() {
    }

    /**
     * Inputs of one level.
     *
     * @param percentile statewide percentile of the assigned school, null when unknown or no school is assigned
     * @param distanceM distance to the assigned school in metres, null when no school is assigned
     * @param choice choice part of the level ({@link SchoolChoiceIndex#choice}), null when unknown
     */
    record Level(Double percentile, Double distanceM, Double choice) {
        static final Level NONE = new Level(null, null, null);
    }

    /**
     * The three parts and the score (0-100, null when unknown), rounded to 2 decimals as stored, with the label of the
     * stored score.
     */
    record Score(Double quality, Double access, Double choice, Double school, String label) { }

    /** Access part of one level: 100 within 1 mile, 75 within 2, 50 within 5, 25 within 10, else 0. */
    static Double access(Double distanceM) {
        if (distanceM == null) return null;
        if (distanceM <= MILE_M) return 100.0;
        if (distanceM <= 2 * MILE_M) return 75.0;
        if (distanceM <= 5 * MILE_M) return 50.0;
        if (distanceM <= 10 * MILE_M) return 25.0;
        return 0.0;
    }

    static Score score(Level elementary, Level middle, Level high) {
        Double quality = WeightedScore.average(LEVEL_WEIGHTS, elementary.percentile(), middle.percentile(),
            high.percentile());
        Double access = WeightedScore.average(LEVEL_WEIGHTS, access(elementary.distanceM()), access(middle.distanceM()),
            access(high.distanceM()));
        Double choice = WeightedScore.average(LEVEL_WEIGHTS, elementary.choice(), middle.choice(), high.choice());
        // without any assigned school (no quality, no access) the choice alone would be the score: no score
        Double school = quality == null && access == null ? null
            : round2(WeightedScore.average(PART_WEIGHTS, quality, access, choice));
        return new Score(round2(quality), round2(access), round2(choice), school, label(school));
    }

    /** Label of the score rounded as it is shown: Excellent (80+), Good (60-79), Fair (40-59), Limited (under 40). */
    static String label(Double score) {
        return score == null ? null : PropertySchoolContextServiceImpl.label((int) Math.round(score));
    }

    /** Rounded half up to 2 decimals, the scale of the stored columns, so the label matches the stored score. */
    static Double round2(Double value) {
        return value == null ? null
            : java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
