package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.service.SchoolAssignmentService.SchoolCounty;

/**
 * School score v2 (SCRUM-203) of the assigned properties, stored in {@code property_school_scores} (migration V49):
 * quality (FLDOE statewide percentile of the assigned schools, 60%), access (distance to them, 25%) and choice (best
 * graded school within 3 miles other than the assigned one, 15%), per level elementary 40%, middle 30%, high 30%.
 */
public interface SchoolScoreV2Service {

    /**
     * What one county's scoring produced and wrote.
     *
     * @param gradeYear FLDOE year the quality and choice parts use, null when no grade is loaded
     * @param properties assigned properties of the county
     * @param scored properties with a score (the others have every part unknown)
     * @param inserted rows added, {@code updated} rows changed, {@code deleted} rows removed; unchanged rows are not
     *                 written
     */
    record ScoreResult(String fips5, Integer gradeYear, int properties, int scored, int inserted, int updated,
            int deleted, long millis) { }

    /**
     * Counties a score v2 run covers: counties with stored assignments.
     *
     * @param fips5 one county (5-digit FIPS code), or null for every such county
     */
    List<SchoolCounty> countiesToScore(String fips5);

    /** Number of assigned properties of the county. */
    int countPropertiesToScore(SchoolCounty county);

    /**
     * Scores every assigned property of one county inside the caller's transaction, from its stored assignments and
     * the newest FLDOE grades. Only the rows that change are written; scores of properties no longer assigned are
     * deleted.
     *
     * @param fips5 5-digit FIPS code of the county
     * @return what was scored and written
     * @throws IllegalStateException when called outside a transaction
     * @throws IllegalArgumentException when no county has this FIPS code
     */
    ScoreResult scoreCounty(String fips5);
}
