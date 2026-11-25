package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.service.impl.SchoolScoreV2.Level;
import com.geointelli.ai.property.service.service.impl.SchoolScoreV2.Score;

/** School score v2 (SCRUM-203): quality 60%, access 25%, choice 15%; elementary 40%, middle 30%, high 30%. */
class SchoolScoreV2Test {

    private static final double MILE = SchoolScoreV2.MILE_M;

    @Nested
    @DisplayName("Score")
    class ScoreTests {

        @Test
        @DisplayName("When every part of every level is known, then use all the weights")
        void whenEverythingIsKnown_thenUseAllWeights() {
            // Arrange: percentiles 90/70/50, schools 0.5, 2.5 and 4 miles away, best other schools 95/80/60
            Level elementary = new Level(90.0, 0.5 * MILE, 95.0);
            Level middle = new Level(70.0, 2.5 * MILE, 80.0);
            Level high = new Level(50.0, 4 * MILE, 60.0);

            // Act
            Score score = SchoolScoreV2.score(elementary, middle, high);

            // Assert: quality 36 + 21 + 15 = 72; access 40 + 15 + 15 = 70; choice 38 + 24 + 18 = 80
            assertEquals(72.0, score.quality());
            assertEquals(70.0, score.access());
            assertEquals(80.0, score.choice());
            // 0.60 * 72 + 0.25 * 70 + 0.15 * 80 = 43.2 + 17.5 + 12
            assertEquals(72.7, score.school());
            assertEquals(SchoolContextDTO.LABEL_GOOD, score.label());
        }

        @Test
        @DisplayName("When no high school is assigned, then score the elementary and middle levels only")
        void whenHighSchoolIsMissing_thenLeaveItOut() {
            // Arrange: no high school row; the choice of the high level still counts
            Level elementary = new Level(90.0, 0.5 * MILE, 95.0);
            Level middle = new Level(70.0, 2.5 * MILE, 80.0);
            Level high = new Level(null, null, 60.0);

            // Act
            Score score = SchoolScoreV2.score(elementary, middle, high);

            // Assert: quality (36 + 21) / 0.7, access (40 + 15) / 0.7, never 0 for the missing school
            assertEquals(81.43, score.quality());
            assertEquals(78.57, score.access());
            assertEquals(80.0, score.choice());
            // 0.60 * 81.43 + 0.25 * 78.57 + 0.15 * 80 = 48.86 + 19.64 + 12
            assertEquals(80.5, score.school());
            assertEquals(SchoolContextDTO.LABEL_EXCELLENT, score.label());
        }

        @Test
        @DisplayName("When the assigned school has grade I (no percentile), then its quality is unknown, not 0")
        void whenSchoolIsNotGraded_thenQualityLeavesItOut() {
            // Arrange: the elementary school is incomplete (I): no statewide percentile
            Level elementary = new Level(null, 0.5 * MILE, 95.0);
            Level middle = new Level(70.0, 2.5 * MILE, 80.0);
            Level high = new Level(50.0, 4 * MILE, 60.0);

            // Act
            Score score = SchoolScoreV2.score(elementary, middle, high);

            // Assert: quality over middle and high only: (21 + 15) / 0.6 = 60; access is still known
            assertEquals(60.0, score.quality());
            assertEquals(70.0, score.access());
        }

        @Test
        @DisplayName("When a school is assigned as the nearest one far away, then its distance counts as known")
        void whenNearestSchoolIsFar_thenAccessIsZeroNotUnknown() {
            // Arrange: no zone; the nearest zoned school is 12 miles away
            Level far = new Level(60.0, 12 * MILE, 0.0);

            // Act
            Score score = SchoolScoreV2.score(far, far, far);

            // Assert: access 0 and choice 0 (no other school within 3 miles) are known values
            assertEquals(0.0, score.access());
            assertEquals(0.0, score.choice());
            assertEquals(36.0, score.school());
            assertEquals(SchoolContextDTO.LABEL_LIMITED, score.label());
        }

        @Test
        @DisplayName("When the zone's school is not in the data (no school at that level), then the level is left out")
        void whenZonedSchoolIsUnmatched_thenLevelLeftOut() {
            // Arrange: elementary zone without a school (UNMATCHED_ZONE): no percentile, no distance; choice still known
            Level unmatched = new Level(null, null, 95.0);
            Level middle = new Level(70.0, 2.5 * MILE, 80.0);
            Level high = new Level(50.0, 4 * MILE, 60.0);

            // Act
            Score score = SchoolScoreV2.score(unmatched, middle, high);

            // Assert: quality (21 + 15) / 0.6 and access (15 + 15) / 0.6, as if the level had no row; no other school
            assertEquals(60.0, score.quality());
            assertEquals(50.0, score.access());
            assertEquals(80.0, score.choice());
            assertEquals(SchoolScoreV2.round2(0.60 * 60 + 0.25 * 50 + 0.15 * 80), score.school());
        }

        @Test
        @DisplayName("When no level has an assigned school, then there is no score, even with a known choice")
        void whenNoAssignedSchool_thenNoScore() {
            // Arrange: every level unmatched; schools nearby
            Level unmatched = new Level(null, null, 90.0);

            // Act
            Score score = SchoolScoreV2.score(unmatched, unmatched, unmatched);

            // Assert: the choice part is kept, but it is not a school score on its own
            assertEquals(90.0, score.choice());
            assertNull(score.school());
            assertNull(score.label());
        }

        @Test
        @DisplayName("When no part is known, then the score and the label are null")
        void whenNothingIsKnown_thenNoScore() {
            // Act
            Score score = SchoolScoreV2.score(Level.NONE, Level.NONE, Level.NONE);

            // Assert
            assertNull(score.quality());
            assertNull(score.access());
            assertNull(score.choice());
            assertNull(score.school());
            assertNull(score.label());
        }
    }

    @ParameterizedTest(name = "{0} miles -> {1}")
    @CsvSource({"0, 100", "1, 100", "1.01, 75", "2, 75", "2.5, 50", "5, 50", "5.01, 25", "10, 25", "10.01, 0"})
    @DisplayName("Access bands: 1, 2, 5 and 10 miles")
    void accessBands(double miles, double expected) {
        assertEquals(expected, SchoolScoreV2.access(miles * MILE));
    }

    @Test
    @DisplayName("An unknown distance has no access part")
    void unknownDistance() {
        assertNull(SchoolScoreV2.access(null));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"80, Excellent", "79.5, Excellent", "79.49, Good", "60, Good", "59.5, Good", "40, Fair", "39.49, Limited",
        "0, Limited"})
    @DisplayName("The label follows the score rounded as it is shown")
    void labels(double score, String label) {
        assertEquals(label, SchoolScoreV2.label(score));
    }
}
