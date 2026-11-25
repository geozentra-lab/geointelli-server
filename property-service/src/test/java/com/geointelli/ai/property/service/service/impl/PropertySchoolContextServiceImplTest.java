package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AssignedSchoolDTO;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PropertySchoolContextServiceImplTest {

    PropertyRepository propertyRepository = mock(PropertyRepository.class);
    SchoolAnalysisService schoolAnalysisService = mock(SchoolAnalysisService.class);

    PropertySchoolContextServiceImpl schoolContextService =
            new PropertySchoolContextServiceImpl(propertyRepository, schoolAnalysisService);

    private static final Long PROPERTY_ID = 42L;

    // stored raw values and part scores; rows scored before the fix store 0 as the part of an unknown value
    private static SchoolAnalysisDTO analysis(Double distPublic, Double distCharter, Integer within1km,
                                              Double publicPart, Double charterPart, Double densityPart) {
        SchoolAnalysisDTO analysis = new SchoolAnalysisDTO();
        analysis.setPropertyId(PROPERTY_ID);
        analysis.setDistPublicSchool(distPublic);
        analysis.setDistCharterSchool(distCharter);
        analysis.setSchoolsWithin1km(within1km);
        analysis.setPublicSchoolDistanceScore(publicPart);
        analysis.setCharterSchoolDistanceScore(charterPart);
        analysis.setSchoolDensityScore(densityPart);
        return analysis;
    }

    private void givenStoredAnalysis(SchoolAnalysisDTO analysis) {
        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(schoolAnalysisService.getPropertySchoolAnalysis(PROPERTY_ID)).thenReturn(Optional.ofNullable(analysis));
    }

    @Nested
    @DisplayName("GetSchoolContext Tests")
    class GetSchoolContext {

        @Test
        @DisplayName("When the analysis is complete, then return AVAILABLE with the stored values, rounded score, label and parts")
        void whenAnalysisIsComplete_thenReturnAvailable() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, 1800.0, 3, 75.0, 25.0, 50.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(PROPERTY_ID, context.getPropertyId());
            assertEquals(800.0, context.getNearestPublicSchoolDistance());
            assertEquals(1800.0, context.getNearestCharterSchoolDistance());
            assertEquals(3, context.getSchoolsWithin1km());
            assertEquals(55, context.getSchoolScore());
            assertEquals(SchoolContextDTO.LABEL_FAIR, context.getSchoolScoreLabel());
            assertEquals(75, context.getPublicSchoolDistanceScore());
            assertEquals(25, context.getCharterSchoolDistanceScore());
            assertEquals(50, context.getSchoolDensityScore());
            assertEquals(SchoolContextDTO.DISTANCE_UNIT, context.getDistanceUnit());
            assertEquals(SchoolContextDTO.SOURCE, context.getSource());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When some values are unknown, then score the known values only and name the unknown ones")
        void whenSomeValuesAreUnknown_thenScoreKnownValuesOnly() {
            // Arrange: an older row stores 0 for the unknown charter part
            givenStoredAnalysis(analysis(800.0, null, 3, 75.0, 0.0, 50.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertNull(context.getNearestCharterSchoolDistance());
            // (75 * 0.4 + 50 * 0.4) / 0.8 = 62.5, not the 50 that counting the unknown part as 0 would give
            assertEquals(63, context.getSchoolScore());
            assertEquals(SchoolContextDTO.LABEL_GOOD, context.getSchoolScoreLabel());
            assertNull(context.getCharterSchoolDistanceScore());
            assertEquals(75, context.getPublicSchoolDistanceScore());
            assertEquals(50, context.getSchoolDensityScore());
            assertEquals("Score based on known values only, unknown values: nearestCharterSchoolDistance",
                    context.getNote());
        }

        @Test
        @DisplayName("When the score is not calculated yet, then return the values without a score or note")
        void whenScoreIsNotCalculated_thenReturnNoScore() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, 1800.0, 3, null, null, null));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertNull(context.getSchoolScore());
            assertNull(context.getSchoolScoreLabel());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When no school is within 1 km, then return NO_NEARBY_RESULTS, keep the distances and the score")
        void whenNoSchoolWithin1km_thenReturnNoNearbyResults() {
            // Arrange
            givenStoredAnalysis(analysis(2500.0, 3200.0, 0, 0.0, 0.0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS, context.getStatus());
            assertEquals(PropertySchoolContextServiceImpl.NO_NEARBY_NOTE, context.getNote());
            assertEquals(0, context.getSchoolsWithin1km());
            assertEquals(2500.0, context.getNearestPublicSchoolDistance());
            assertEquals(3200.0, context.getNearestCharterSchoolDistance());
            assertEquals(0, context.getSchoolScore());
            assertEquals(SchoolContextDTO.LABEL_LIMITED, context.getSchoolScoreLabel());
        }

        @Test
        @DisplayName("When no school is within 1 km and distances are unknown, then the note names both")
        void whenNoSchoolWithin1kmAndDistancesUnknown_thenNoteNamesBoth() {
            // Arrange
            givenStoredAnalysis(analysis(null, null, 0, 0.0, 0.0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS, context.getStatus());
            assertEquals(0, context.getSchoolScore());
            assertEquals("No school within 1 km. Score based on known values only, unknown values: "
                    + "nearestPublicSchoolDistance, nearestCharterSchoolDistance", context.getNote());
        }

        @Test
        @DisplayName("When the count is unknown but both distances are over 1 km, then return NO_NEARBY_RESULTS")
        void whenCountUnknownAndBothDistancesFar_thenReturnNoNearbyResults() {
            // Arrange
            givenStoredAnalysis(analysis(2500.0, 3200.0, null, 0.0, 0.0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS, context.getStatus());
            assertEquals(0, context.getSchoolScore());
            assertEquals("No public or charter school within 1 km. Score based on known values only, unknown values: "
                    + "schoolsWithin1km", context.getNote());
        }

        @Test
        @DisplayName("When the count is unknown and a school is within 1 km, then return AVAILABLE")
        void whenCountUnknownAndSchoolNearby_thenReturnAvailable() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, 3200.0, null, 75.0, 0.0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertNull(context.getSchoolDensityScore());
            // (75 * 0.4 + 0 * 0.2) / 0.6 = 50
            assertEquals(50, context.getSchoolScore());
            assertEquals("Score based on known values only, unknown values: schoolsWithin1km", context.getNote());
        }

        @Test
        @DisplayName("When the property has no school analysis, then return NO_COVERAGE without values")
        void whenNoAnalysis_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(null);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_COVERAGE, context.getStatus());
            assertEquals(PropertySchoolContextServiceImpl.NO_COVERAGE_NOTE, context.getNote());
            assertEquals(SchoolContextDTO.SOURCE, context.getSource());
            assertNull(context.getNearestPublicSchoolDistance());
            assertNull(context.getNearestCharterSchoolDistance());
            assertNull(context.getSchoolsWithin1km());
            assertNull(context.getSchoolScore());
            assertNull(context.getPublicSchoolDistanceScore());
        }

        @Test
        @DisplayName("When the analysis has no value, then return NO_COVERAGE")
        void whenAnalysisHasNoValue_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(analysis(null, null, null, 0.0, 0.0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_COVERAGE, context.getStatus());
            assertNull(context.getSchoolScore());
            assertNull(context.getSchoolDensityScore());
        }

        @Test
        @DisplayName("When the analysis exists, then do not query the property separately")
        void whenAnalysisExists_thenSkipPropertyLookup() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, 1800.0, 3, 75.0, 25.0, 50.0));

            // Act
            schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            verify(propertyRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("When the ID is 0 or null, then throw IllegalArgumentException without reading data")
        void whenIdIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> schoolContextService.getSchoolContext(0L));
            assertThrows(IllegalArgumentException.class, () -> schoolContextService.getSchoolContext(null));
            verifyNoInteractions(propertyRepository, schoolAnalysisService);
        }

        @Test
        @DisplayName("When the property does not exist, then throw PropertyNotFoundException")
        void whenPropertyDoesNotExist_thenThrowPropertyNotFoundException() {
            // Arrange
            when(schoolAnalysisService.getPropertySchoolAnalysis(999L)).thenReturn(Optional.empty());
            when(propertyRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException exception = assertThrows(PropertyNotFoundException.class,
                    () -> schoolContextService.getSchoolContext(999L));
            assertEquals("No property found with id: 999", exception.getMessage());
        }
    }

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({"100, Excellent", "80, Excellent", "79, Good", "60, Good", "59, Fair", "40, Fair", "39, Limited", "0, Limited"})
    @DisplayName("Score labels follow the 80 / 60 / 40 boundaries")
    void labelBoundaries(int score, String label) {
        assertEquals(label, PropertySchoolContextServiceImpl.label(score));
    }

    @Nested
    @DisplayName("School Score V2 Tests (SCRUM-203)")
    class ScoreV2 {

        private AssignedSchoolDTO assigned(String level, String method, Integer percentile) {
            AssignedSchoolDTO school = new AssignedSchoolDTO();
            school.setLevel(level);
            school.setName(level + " SCHOOL");
            school.setAssignmentMethod(method);
            school.setStatewidePercentile(percentile);
            school.setGradeYear(2026);
            school.setDistanceMeters(900.0);
            return school;
        }

        private SchoolAnalysisDTO withScoreV2(SchoolAnalysisDTO analysis, List<AssignedSchoolDTO> schools) {
            analysis.setAssignedSchools(schools);
            analysis.setQualityScore(71.43);
            analysis.setAccessScore(77.5);
            analysis.setChoiceScore(24.0);
            analysis.setSchoolScoreV2(65.83);
            analysis.setSchoolScoreV2Label(SchoolContextDTO.LABEL_GOOD);
            analysis.setGradeYear(2026);
            analysis.setScoreVersion(2);
            return analysis;
        }

        private SchoolAnalysisDTO v2Only(List<AssignedSchoolDTO> schools) {
            SchoolAnalysisDTO analysis = new SchoolAnalysisDTO();
            analysis.setPropertyId(PROPERTY_ID);
            return withScoreV2(analysis, schools);
        }

        @Test
        @DisplayName("When the property has assigned schools but no v1 row, then return AVAILABLE with the v2 score")
        void whenOnlyV2_thenAvailableWithScoreV2() {
            // Arrange
            List<AssignedSchoolDTO> schools = List.of(assigned("ELEMENTARY", "ZONE", 80),
                    assigned("MIDDLE", "ZONE", 60), assigned("HIGH", "ZONE", 40));
            givenStoredAnalysis(v2Only(schools));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(schools, context.getAssignedSchools());
            assertEquals(66, context.getSchoolScoreV2());
            assertEquals(SchoolContextDTO.LABEL_GOOD, context.getSchoolScoreV2Label());
            assertEquals(71, context.getQualityScore());
            assertEquals(78, context.getAccessScore());
            assertEquals(24, context.getChoiceScore());
            assertEquals(2026, context.getGradeYear());
            assertEquals(2, context.getScoreVersion());
            assertEquals("FLDOE School Grades 2026, " + SchoolContextDTO.SOURCE_V2, context.getSource());
            assertNull(context.getSchoolScore());
            assertNull(context.getNearestPublicSchoolDistance());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When v1 finds no school within 1 km but schools are assigned, then AVAILABLE and the v1 note is marked")
        void whenV1HasNoNearbySchool_thenAssignedSchoolsDecide() {
            // Arrange
            SchoolAnalysisDTO analysis = withScoreV2(analysis(1500.0, 2500.0, 0, 50.0, 0.0, 0.0),
                    List.of(assigned("ELEMENTARY", "ZONE", 80), assigned("MIDDLE", "ZONE", 60),
                            assigned("HIGH", "ZONE", 40)));
            givenStoredAnalysis(analysis);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert: the v1 fields are still filled for one release
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(0, context.getSchoolsWithin1km());
            assertEquals(66, context.getSchoolScoreV2());
            assertEquals(PropertySchoolContextServiceImpl.V1_NOTE_PREFIX + PropertySchoolContextServiceImpl.NO_NEARBY_NOTE,
                    context.getNote());
        }

        @Test
        @DisplayName("When a level has no school or only the nearest one, and the score is pending, then the notes say so")
        void whenLevelsMissingOrNearest_thenNotes() {
            // Arrange: no middle school, the high school is the nearest one, no score yet
            SchoolAnalysisDTO analysis = new SchoolAnalysisDTO();
            analysis.setPropertyId(PROPERTY_ID);
            analysis.setAssignedSchools(List.of(assigned("ELEMENTARY", "ZONE", 80), assigned("HIGH", "NEAREST", null)));
            givenStoredAnalysis(analysis);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertNull(context.getSchoolScoreV2());
            assertEquals(2026, context.getGradeYear(), "the year of the assigned schools' grades");
            assertEquals(PropertySchoolContextServiceImpl.NO_ASSIGNED_SCHOOL_NOTE + "MIDDLE. "
                    + PropertySchoolContextServiceImpl.NEAREST_NOTE + "HIGH. "
                    + PropertySchoolContextServiceImpl.SCORE_V2_PENDING_NOTE, context.getNote());
        }

        @Test
        @DisplayName("When a level's zoned school is not in the school data, then the note says it is left out")
        void whenZonedSchoolUnmatched_thenNote() {
            // Arrange: elementary UNMATCHED_ZONE, the other levels zoned; quality and access come from them
            SchoolAnalysisDTO analysis = v2Only(List.of(assigned("ELEMENTARY", "UNMATCHED_ZONE", null),
                    assigned("MIDDLE", "ZONE", 60), assigned("HIGH", "ZONE", 40)));
            givenStoredAnalysis(analysis);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(PropertySchoolContextServiceImpl.UNMATCHED_ZONE_NOTE + "ELEMENTARY", context.getNote());
        }

        @Test
        @DisplayName("When the stored v2 row has no score (no level has a school), then the note says it is unavailable")
        void whenNoSchoolKnown_thenUnavailable() {
            // Arrange: every level UNMATCHED_ZONE; the row exists (scoreVersion 2) with only the choice part
            SchoolAnalysisDTO analysis = v2Only(List.of(assigned("ELEMENTARY", "UNMATCHED_ZONE", null),
                    assigned("MIDDLE", "UNMATCHED_ZONE", null), assigned("HIGH", "UNMATCHED_ZONE", null)));
            analysis.setQualityScore(null);
            analysis.setAccessScore(null);
            analysis.setSchoolScoreV2(null);
            analysis.setSchoolScoreV2Label(null);
            givenStoredAnalysis(analysis);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertNull(context.getSchoolScoreV2());
            assertEquals(PropertySchoolContextServiceImpl.UNMATCHED_ZONE_NOTE + "ELEMENTARY, MIDDLE, HIGH. "
                    + PropertySchoolContextServiceImpl.SCORE_V2_UNAVAILABLE_NOTE, context.getNote());
        }

        @Test
        @DisplayName("When a part of the v2 score is unknown, then the note names it")
        void whenPartUnknown_thenNote() {
            // Arrange: none of the assigned schools is graded
            SchoolAnalysisDTO analysis = v2Only(List.of(assigned("ELEMENTARY", "ZONE", null),
                    assigned("MIDDLE", "ZONE", null), assigned("HIGH", "ZONE", null)));
            analysis.setQualityScore(null);
            givenStoredAnalysis(analysis);

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertNull(context.getQualityScore());
            assertEquals("School score v2 based on known parts only, unknown parts: qualityScore", context.getNote());
        }
    }
}
