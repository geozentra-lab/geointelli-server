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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PropertyTransportContextServiceImplTest {
    PropertyRepository propertyRepository = mock(PropertyRepository.class);
    TransportationAnalysisService transportationAnalysisService = mock(TransportationAnalysisService.class);

    PropertyTransportContextServiceImpl transportContextService =
            new PropertyTransportContextServiceImpl(propertyRepository, transportationAnalysisService);

    private static final Long PROPERTY_ID = 42L;

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    // raw values and the part scores stored for them: rail, mover, bus stop density, bus stop distance, highway
    private static TransportationAnalysisDTO analysis(String rail, String mover, String bus, String highway,
                                                      Integer busStopsWithin500m, String railPart, String moverPart,
                                                      String densityPart, String busPart, String highwayPart) {
        TransportationAnalysisDTO analysis = new TransportationAnalysisDTO();
        analysis.setPropertyId(PROPERTY_ID);
        analysis.setDistMetroRail(decimal(rail));
        analysis.setDistMetroMover(decimal(mover));
        analysis.setDistBusStop(decimal(bus));
        analysis.setDistHighway(decimal(highway));
        analysis.setBusStopsWithin500m(busStopsWithin500m);
        analysis.setMetroRailScore(decimal(railPart));
        analysis.setMetroMoverScore(decimal(moverPart));
        analysis.setBusStopDensityScore(decimal(densityPart));
        analysis.setBusStopDistanceScore(decimal(busPart));
        analysis.setHighwayScore(decimal(highwayPart));
        // stale stored score: the context recalculates it from the known parts
        analysis.setTransportationScore(new BigDecimal("1"));
        return analysis;
    }

    private void givenStoredAnalysis(TransportationAnalysisDTO analysis) {
        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(transportationAnalysisService.getPropertyTransportationAnalysis(PROPERTY_ID))
                .thenReturn(Optional.ofNullable(analysis));
    }

    @Nested
    @DisplayName("GetTransportContext Tests")
    class GetTransportContext {

        @Test
        @DisplayName("When the analysis is complete, then return AVAILABLE with the 4 normalized categories and the score")
        void whenAnalysisIsComplete_thenReturnAvailableWithNormalizedCategories() {
            // Arrange: 0.4*50 + 0.2*0 + 0.2*50 + 0.1*100 + 0.1*75 = 47.5
            givenStoredAnalysis(analysis("1200", "3400", "150", "900", 4, "50", "0", "50", "100", "75"));

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
            assertEquals(48, context.getTransportationScore());
            assertEquals(TransportContextDTO.LABEL_FAIR, context.getTransportationScoreLabel());
            assertEquals(TransportContextDTO.DISTANCE_UNIT, context.getDistanceUnit());
            assertEquals(TransportContextDTO.SOURCE, context.getSource());
            assertNull(context.getError());
            assertNull(context.getNote());
            verify(propertyRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("When a distance is unknown, then leave it out of the list and the score, even if stored as 0")
        void whenDistanceIsUnknown_thenLeaveItOutOfListAndScore() {
            // Arrange: older row stored the unknown mover part as 0; (20 + 10 + 10 + 7.5) / 0.8 = 59.4
            givenStoredAnalysis(analysis("1200", null, "150", "900", 4, "50", "0", "50", "100", "75"));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(List.of(TransportCategory.RAIL, TransportCategory.BUS, TransportCategory.HIGHWAY),
                    context.getTransport().stream().map(TransportOptionDTO::getCategory).toList());
            assertEquals(59, context.getTransportationScore());
            assertEquals(TransportContextDTO.LABEL_FAIR, context.getTransportationScoreLabel());
            assertEquals("Score based on known values only, unknown values: PEOPLE_MOVER", context.getNote());
        }

        @Test
        @DisplayName("When the bus stop count is unknown, then leave it out of the score and name it in the note")
        void whenBusStopCountIsUnknown_thenLeaveItOutOfScore() {
            // Arrange: (0.4*100 + 0.2*100 + 0.1*100 + 0.1*100) / 0.8 = 100
            givenStoredAnalysis(analysis("300", "400", "100", "200", null, "100", "100", "0", "100", "100"));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertNull(context.getBusStopsWithin500m());
            assertEquals(100, context.getTransportationScore());
            assertEquals(TransportContextDTO.LABEL_EXCELLENT, context.getTransportationScoreLabel());
            assertEquals("Score based on known values only, unknown values: busStopsWithin500m", context.getNote());
        }

        @Test
        @DisplayName("When the analysis is not scored yet, then return the distances without a score")
        void whenAnalysisIsNotScored_thenReturnNoScore() {
            // Arrange
            givenStoredAnalysis(analysis("1200", null, "150", "900", 4, null, null, null, null, null));

            // Act
            TransportContextDTO context = transportContextService.getTransportContext(PROPERTY_ID);

            // Assert
            assertEquals(TransportContextDTO.STATUS_AVAILABLE, context.getStatus());
            assertEquals(3, context.getTransport().size());
            assertNull(context.getTransportationScore());
            assertNull(context.getTransportationScoreLabel());
            assertEquals("Score unavailable, unknown values: PEOPLE_MOVER", context.getNote());
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
            assertNull(context.getTransportationScoreLabel());
            assertNull(context.getError());
        }

        @Test
        @DisplayName("When the analysis has no value, then return NO_COVERAGE")
        void whenAnalysisHasNoValue_thenReturnNoCoverage() {
            // Arrange
            givenStoredAnalysis(analysis(null, null, null, null, null, "0", "0", "0", "0", "0"));

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
            when(transportationAnalysisService.getPropertyTransportationAnalysis(PROPERTY_ID))
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
        @DisplayName("When reading fails for another reason than the data source, then rethrow it")
        void whenReadingFailsWithBug_thenRethrow() {
            // Arrange
            when(transportationAnalysisService.getPropertyTransportationAnalysis(PROPERTY_ID))
                    .thenThrow(new IllegalStateException("mapping failed"));

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> transportContextService.getTransportContext(PROPERTY_ID));
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
            when(transportationAnalysisService.getPropertyTransportationAnalysis(999L)).thenReturn(Optional.empty());
            when(propertyRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException exception = assertThrows(PropertyNotFoundException.class,
                    () -> transportContextService.getTransportContext(999L));
            assertEquals("No property found with id: 999", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Label Tests")
    class Label {

        @Test
        @DisplayName("When a score is given, then return the label of its band")
        void whenScoreIsGiven_thenReturnLabelOfItsBand() {
            assertEquals(TransportContextDTO.LABEL_EXCELLENT, PropertyTransportContextServiceImpl.label(80));
            assertEquals(TransportContextDTO.LABEL_GOOD, PropertyTransportContextServiceImpl.label(79));
            assertEquals(TransportContextDTO.LABEL_GOOD, PropertyTransportContextServiceImpl.label(60));
            assertEquals(TransportContextDTO.LABEL_FAIR, PropertyTransportContextServiceImpl.label(40));
            assertEquals(TransportContextDTO.LABEL_LIMITED, PropertyTransportContextServiceImpl.label(39));
            assertNull(PropertyTransportContextServiceImpl.label(null));
        }
    }
}
