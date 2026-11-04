package com.geointelli.ai.property.service.service.impl;

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
}
