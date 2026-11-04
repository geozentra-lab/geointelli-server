package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.entity.TransportationAnalysis;
import com.geointelli.ai.property.service.mapper.TransportationAnalysisMapper;
import com.geointelli.ai.property.service.repository.TransportationAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class TransportationAnalysisServiceImplTest {

    TransportationAnalysisRepository transportationAnalysisRepository = mock(TransportationAnalysisRepository.class);
    TransportationAnalysisMapper transportationAnalysisMapper = mock(TransportationAnalysisMapper.class);

    TransportationAnalysisServiceImpl transportationAnalysisService =
            new TransportationAnalysisServiceImpl(transportationAnalysisRepository, transportationAnalysisMapper);

    private static TransportationAnalysis analysis(Long id, Double metroRail, Double metroMover, Double busStop,
                                                   Double highway, Integer busStopsWithin500m) {
        TransportationAnalysis analysis = new TransportationAnalysis();
        analysis.setId(id);
        analysis.setPropertyId(id);
        analysis.setDistMetroRail(metroRail);
        analysis.setDistMetroMover(metroMover);
        analysis.setDistBusStop(busStop);
        analysis.setDistHighway(highway);
        analysis.setBusStopsWithin500m(busStopsWithin500m);
        return analysis;
    }

    @Nested
    @DisplayName("Score Tests")
    class Score {

        @Test
        @DisplayName("When every value is known, then use the 40/20/20/10/10 weights")
        void whenEveryValueIsKnown_thenUseAllWeights() {
            // Arrange
            TransportationAnalysis analysis = analysis(1L, 300.0, 1200.0, 300.0, 2500.0, 3);

            // Act
            transportationAnalysisService.saveScore(analysis);

            // Assert: 100 * 0.4 + 50 * 0.2 + 50 * 0.2 + 100 * 0.1 + 0 * 0.1
            assertEquals(70.0, analysis.getTransportationScore(), 1e-9);
            assertEquals(0.0, analysis.getHighwayScore());
        }

        @Test
        @DisplayName("When values are unknown, then leave their parts out instead of counting them as 0")
        void whenValuesAreUnknown_thenLeaveTheirPartsOut() {
            // Arrange: no metro mover or highway data
            TransportationAnalysis analysis = analysis(1L, 300.0, null, 300.0, null, 3);

            // Act
            transportationAnalysisService.saveScore(analysis);

            // Assert: (100 * 0.4 + 50 * 0.2 + 100 * 0.1) / 0.7, where counting them as 0 gave 60
            assertNull(analysis.getMetroMoverScore());
            assertNull(analysis.getHighwayScore());
            assertEquals(60.0 / 0.7, analysis.getTransportationScore(), 1e-9);
        }

        @Test
        @DisplayName("When no bus stop is within 500 m, then the density part is a real 0")
        void whenNoBusStopNearby_thenDensityIsZero() {
            // Arrange
            TransportationAnalysis analysis = analysis(1L, null, null, null, null, 0);

            // Act
            transportationAnalysisService.saveScore(analysis);

            // Assert
            assertEquals(0.0, analysis.getBusStopDensityScore());
            assertEquals(0.0, analysis.getTransportationScore(), 1e-9);
        }

        @Test
        @DisplayName("When no value is known, then store no score")
        void whenNoValueIsKnown_thenNoScore() {
            // Arrange
            TransportationAnalysis analysis = analysis(1L, null, null, null, null, null);

            // Act
            transportationAnalysisService.saveScore(analysis);

            // Assert
            assertNull(analysis.getBusStopDensityScore());
            assertNull(analysis.getTransportationScore());
        }
    }

    @Nested
    @DisplayName("CalculateAndSaveAllScores Tests")
    class CalculateAll {

        @Test
        @DisplayName("When there are more rows than one batch, then continue after the last id of each batch")
        void whenMoreRowsThanOneBatch_thenWalkByLastId() {
            // Arrange
            int size = TransportationAnalysisServiceImpl.BATCH_SIZE;
            List<TransportationAnalysis> first = LongStream.rangeClosed(1, size)
                    .mapToObj(id -> analysis(id, 300.0, 300.0, 300.0, 300.0, 3)).toList();
            List<TransportationAnalysis> second = List.of(analysis(size + 1L, 300.0, 300.0, 300.0, 300.0, 3));
            when(transportationAnalysisRepository
                    .findTop2000ByTransportationScoreIsNullAndIdGreaterThanOrderByIdAsc(0L)).thenReturn(first);
            when(transportationAnalysisRepository
                    .findTop2000ByTransportationScoreIsNullAndIdGreaterThanOrderByIdAsc((long) size)).thenReturn(second);

            // Act
            transportationAnalysisService.calculateAndSaveAllScores(false);

            // Assert
            assertNotNull(second.get(0).getTransportationScore());
            verify(transportationAnalysisRepository, times(2)).saveAll(anyList());
            verify(transportationAnalysisRepository, never()).findTop2000ByIdGreaterThanOrderByIdAsc(anyLong());
        }

        @Test
        @DisplayName("When recalculating existing scores, then read every row, not only the unscored ones")
        void whenRecalculatingExisting_thenReadEveryRow() {
            // Arrange
            TransportationAnalysis scoredBeforeFix = analysis(1L, 300.0, null, 300.0, null, 3);
            scoredBeforeFix.setTransportationScore(60.0);
            when(transportationAnalysisRepository.findTop2000ByIdGreaterThanOrderByIdAsc(0L))
                    .thenReturn(List.of(scoredBeforeFix));

            // Act
            transportationAnalysisService.calculateAndSaveAllScores(true);

            // Assert
            assertEquals(60.0 / 0.7, scoredBeforeFix.getTransportationScore(), 1e-9);
            verify(transportationAnalysisRepository, never())
                    .findTop2000ByTransportationScoreIsNullAndIdGreaterThanOrderByIdAsc(anyLong());
        }
    }
}
