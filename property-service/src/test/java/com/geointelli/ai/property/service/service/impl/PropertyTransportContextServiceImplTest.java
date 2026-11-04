package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.TransportContextDTO;
import com.geointelli.ai.property.service.dto.TransportOptionDTO;
import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.enums.TransportCategory;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.TransportationAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PropertyTransportContextServiceImplTest {
    PropertyRepository propertyRepository = mock(PropertyRepository.class);
    TransportationAnalysisService transportationAnalysisService = mock(TransportationAnalysisService.class);

    PropertyTransportContextServiceImpl transportContextService =
            new PropertyTransportContextServiceImpl(propertyRepository, transportationAnalysisService);

    private static final Long PROPERTY_ID = 42L;
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static TransportationAnalysisDTO analysis(String rail, String mover, String bus, String highway,
                                                      Integer busStopsWithin500m) {
        TransportationAnalysisDTO analysis = new TransportationAnalysisDTO();
        analysis.setPropertyId(PROPERTY_ID);
        analysis.setDistMetroRail(rail == null ? null : new BigDecimal(rail));
        analysis.setDistMetroMover(mover == null ? null : new BigDecimal(mover));
        analysis.setDistBusStop(bus == null ? null : new BigDecimal(bus));
        analysis.setDistHighway(highway == null ? null : new BigDecimal(highway));
        analysis.setBusStopsWithin500m(busStopsWithin500m);
        analysis.setTransportationScore(new BigDecimal("52.5"));
        analysis.setUpdatedAt(UPDATED_AT);
        return analysis;
    }

    private void givenStoredAnalysis(TransportationAnalysisDTO analysis) {
        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(transportationAnalysisService.getPropertyTransportationAnalysisByIds(List.of(PROPERTY_ID)))
                .thenReturn(analysis == null ? List.of() : List.of(analysis));
    }

    @Nested
    @DisplayName("GetTransportContext Tests")
    class GetTransportContext {

        @Test
        @DisplayName("When the analysis is complete, then return AVAILABLE with the 4 normalized categories in order")
        void whenAnalysisIsComplete_thenReturnAvailableWithNormalizedCategories() {
            // Arrange
            givenStoredAnalysis(analysis("1200", "3400", "150", "900", 4));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(List.of(
                    new TransportOptionDTO(TransportCategory.RAIL, new BigDecimal("1200")),
                    new TransportOptionDTO(TransportCategory.PEOPLE_MOVER, new BigDecimal("3400")),
                    new TransportOptionDTO(TransportCategory.BUS, new BigDecimal("150")),
                    new TransportOptionDTO(TransportCategory.HIGHWAY, new BigDecimal("900"))), context.getTransport());
            assertEquals(4, context.getBusStopsWithin500m());
            assertEquals(new BigDecimal("52.5"), context.getTransportationScore());
            assertEquals(TransportContextDTO.DISTANCE_UNIT, context.getDistanceUnit());
            assertEquals(TransportContextDTO.SOURCE, context.getSource());
            assertEquals(UPDATED_AT, context.getDataUpdatedAt());
            assertNull(context.getError());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When a distance is unknown, then leave that category out and name it in the note, never 0")
        void whenDistanceIsUnknown_thenLeaveCategoryOutAndNameItInNote() {
            // Arrange
            givenStoredAnalysis(analysis("1200", null, "150", "900", 4));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(List.of(TransportCategory.RAIL, TransportCategory.BUS, TransportCategory.HIGHWAY),
                    context.getTransport().stream().map(TransportOptionDTO::getCategory).toList());
            assertEquals("Unknown distances: PEOPLE_MOVER", context.getNote());
        }

        @Test
        @DisplayName("When the property has no transportation analysis, then return NO_COVERAGE with an empty list")
        void whenNoAnalysis_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(null);

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_NO_COVERAGE, context.getStatus());
            assertEquals(PropertyTransportContextServiceImpl.NO_COVERAGE_NOTE, context.getNote());
            assertTrue(context.getTransport().isEmpty());
            assertNull(context.getBusStopsWithin500m());
            assertNull(context.getTransportationScore());
            assertNull(context.getDataUpdatedAt());
            assertNull(context.getError());
        }

        @Test
        @DisplayName("When the analysis has no value, then return NO_COVERAGE")
        void whenAnalysisHasNoValue_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(analysis(null, null, null, null, null));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_NO_COVERAGE, context.getStatus());
            assertTrue(context.getTransport().isEmpty());
            assertNull(context.getTransportationScore());
        }

        @Test
        @DisplayName("When the transport data source fails, then return UNAVAILABLE instead of throwing")
        void whenTransportSourceFails_thenReturnUnavailable() {
            // Arrange
            when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
            when(transportationAnalysisService.getPropertyTransportationAnalysisByIds(List.of(PROPERTY_ID)))
                    .thenThrow(new DataAccessResourceFailureException("connection refused"));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_UNAVAILABLE, context.getStatus());
            assertEquals(PropertyTransportContextServiceImpl.UNAVAILABLE_ERROR, context.getError());
            assertTrue(context.getTransport().isEmpty());
            assertEquals(PROPERTY_ID, context.getPropertyId());
            assertEquals(TransportContextDTO.SOURCE, context.getSource());
            assertNull(context.getNote());
        }

        @Test
        @DisplayName("When the ID is 0 or null, then throw IllegalArgumentException without reading data")
        void whenIdIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> transportContextService.getTransportContext(0L));
            assertThrows(IllegalArgumentException.class, () -> transportContextService.getTransportContext(null));
            verifyNoInteractions(propertyRepository, transportationAnalysisService);
        }

        @Test
        @DisplayName("When the property does not exist, then throw PropertyNotFoundException")
        void whenPropertyDoesNotExist_thenThrowPropertyNotFoundException() {
            // Arrange
            when(propertyRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException exception = assertThrows(PropertyNotFoundException.class,
                    () -> transportContextService.getTransportContext(999L));
            assertEquals("No property found with id: 999", exception.getMessage());
            verifyNoInteractions(transportationAnalysisService);
        }
    }
}
