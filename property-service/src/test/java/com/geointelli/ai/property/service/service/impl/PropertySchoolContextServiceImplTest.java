package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PropertySchoolContextServiceImplTest {

    PropertyRepository propertyRepository = mock(PropertyRepository.class);
    SchoolAnalysisService schoolAnalysisService = mock(SchoolAnalysisService.class);

    PropertySchoolContextServiceImpl schoolContextService =
            new PropertySchoolContextServiceImpl(propertyRepository, schoolAnalysisService);

    private static final Long PROPERTY_ID = 42L;
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static SchoolAnalysisDTO analysis(Double distPublic, Double distCharter, Integer within1km, Double score) {
        SchoolAnalysisDTO analysis = new SchoolAnalysisDTO();
        analysis.setPropertyId(PROPERTY_ID);
        analysis.setDistPublicSchool(distPublic);
        analysis.setDistCharterSchool(distCharter);
        analysis.setSchoolsWithin1km(within1km);
        analysis.setSchoolScore(score);
        analysis.setUpdatedAt(UPDATED_AT);
        return analysis;
    }

    private void givenStoredAnalysis(SchoolAnalysisDTO analysis) {
        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(schoolAnalysisService.getPropertySchoolAnalysisByIds(List.of(PROPERTY_ID)))
                .thenReturn(analysis == null ? List.of() : List.of(analysis));
    }

    @Nested
    @DisplayName("GetSchoolContext Tests")
    class GetSchoolContext {

        @Test
        @DisplayName("When the analysis is complete, then return AVAILABLE with the stored values, unit, source and freshness")
        void whenAnalysisIsComplete_thenReturnAvailable() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, 1800.0, 3, 55.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(PROPERTY_ID, context.getPropertyId());
            assertEquals(800.0, context.getNearestPublicSchoolDistance());
            assertEquals(1800.0, context.getNearestCharterSchoolDistance());
            assertEquals(3, context.getSchoolsWithin1km());
            assertEquals(55.0, context.getSchoolScore());
            assertEquals(SchoolContextDTO.DISTANCE_UNIT, context.getDistanceUnit());
            assertEquals(SchoolContextDTO.SOURCE, context.getSource());
            assertEquals(UPDATED_AT, context.getDataUpdatedAt());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When no school is within 1 km, then return NO_NEARBY_RESULTS and keep the distances")
        void whenNoSchoolWithin1km_thenReturnNoNearbyResults() {
            // Arrange
            givenStoredAnalysis(analysis(2500.0, 3200.0, 0, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS, context.getStatus());
            assertEquals(PropertySchoolContextServiceImpl.NO_NEARBY_NOTE, context.getNote());
            assertEquals(0, context.getSchoolsWithin1km());
            assertEquals(2500.0, context.getNearestPublicSchoolDistance());
            assertEquals(3200.0, context.getNearestCharterSchoolDistance());
            assertEquals(UPDATED_AT, context.getDataUpdatedAt());
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
            assertNull(context.getDataUpdatedAt());
        }

        @Test
        @DisplayName("When the analysis has no value, then return NO_COVERAGE")
        void whenAnalysisHasNoValue_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(analysis(null, null, null, 0.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_NO_COVERAGE, context.getStatus());
            assertNull(context.getSchoolScore());
            assertNull(context.getDataUpdatedAt());
        }

        @Test
        @DisplayName("When some values are unknown, then return AVAILABLE with a note naming them, never 0")
        void whenSomeValuesAreUnknown_thenReturnAvailableWithNote() {
            // Arrange
            givenStoredAnalysis(analysis(800.0, null, 3, 50.0));

            // Act
            SchoolContextDTO context = schoolContextService.getSchoolContext(PROPERTY_ID);

            // Assert
            assertEquals(SchoolContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertNull(context.getNearestCharterSchoolDistance());
            assertEquals("Unknown values: nearestCharterSchoolDistance", context.getNote());
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
            when(propertyRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException exception = assertThrows(PropertyNotFoundException.class,
                    () -> schoolContextService.getSchoolContext(999L));
            assertEquals("No property found with id: 999", exception.getMessage());
            verifyNoInteractions(schoolAnalysisService);
        }
    }
}
