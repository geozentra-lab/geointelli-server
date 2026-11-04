package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.entity.SchoolAnalysis;
import com.geointelli.ai.property.service.mapper.SchoolAnalysisMapper;
import com.geointelli.ai.property.service.repository.SchoolAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class SchoolAnalysisServiceImplTest {

    SchoolAnalysisRepository schoolAnalysisRepository = mock(SchoolAnalysisRepository.class);
    SchoolAnalysisMapper schoolAnalysisMapper = mock(SchoolAnalysisMapper.class);

    SchoolAnalysisServiceImpl schoolAnalysisService =
            new SchoolAnalysisServiceImpl(schoolAnalysisRepository, schoolAnalysisMapper);

    private static SchoolAnalysis analysis(Long id, Double distPublic, Double distCharter, Integer within1km) {
        SchoolAnalysis analysis = new SchoolAnalysis();
        analysis.setId(id);
        analysis.setPropertyId(id);
        analysis.setDistPublicSchool(distPublic);
        analysis.setDistCharterSchool(distCharter);
        analysis.setSchoolsWithin1km(within1km);
        return analysis;
    }

    private static List<SchoolAnalysis> rows(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId).mapToObj(id -> analysis(id, 300.0, 1200.0, 3)).toList();
    }

    @Nested
    @DisplayName("Score Tests")
    class Score {

        @Test
        @DisplayName("When every value is known, then use the 40/20/40 weights")
        void whenEveryValueIsKnown_thenUseAllWeights() {
            // Arrange
            SchoolAnalysis analysis = analysis(1L, 300.0, 1200.0, 3);

            // Act
            schoolAnalysisService.saveScore(analysis);

            // Assert: 100 * 0.4 + 50 * 0.2 + 50 * 0.4
            assertEquals(100.0, analysis.getPublicSchoolDistanceScore());
            assertEquals(50.0, analysis.getCharterSchoolDistanceScore());
            assertEquals(50.0, analysis.getSchoolDensityScore());
            assertEquals(70.0, analysis.getSchoolScore(), 1e-9);
        }

        @Test
        @DisplayName("When a value is unknown, then leave its part out instead of counting it as 0")
        void whenValueIsUnknown_thenLeaveItsPartOut() {
            // Arrange
            SchoolAnalysis analysis = analysis(1L, 300.0, null, 3);

            // Act
            schoolAnalysisService.saveScore(analysis);

            // Assert: (100 * 0.4 + 50 * 0.4) / 0.8, where counting it as 0 gave 60
            assertNull(analysis.getCharterSchoolDistanceScore());
            assertEquals(75.0, analysis.getSchoolScore(), 1e-9);
        }

        @Test
        @DisplayName("When no school is within 1 km, then the density part is a real 0")
        void whenNoSchoolWithin1km_thenDensityIsZero() {
            // Arrange
            SchoolAnalysis analysis = analysis(1L, 2500.0, 3200.0, 0);

            // Act
            schoolAnalysisService.saveScore(analysis);

            // Assert
            assertEquals(0.0, analysis.getSchoolDensityScore());
            assertEquals(0.0, analysis.getSchoolScore(), 1e-9);
        }

        @Test
        @DisplayName("When no value is known, then store no score")
        void whenNoValueIsKnown_thenNoScore() {
            // Arrange
            SchoolAnalysis analysis = analysis(1L, null, null, null);

            // Act
            schoolAnalysisService.saveScore(analysis);

            // Assert
            assertNull(analysis.getPublicSchoolDistanceScore());
            assertNull(analysis.getSchoolDensityScore());
            assertNull(analysis.getSchoolScore());
        }
    }

    @Nested
    @DisplayName("CalculateAndSaveAllScores Tests")
    class CalculateAll {

        @Test
        @DisplayName("When there are more rows than one batch, then continue after the last id of each batch")
        void whenMoreRowsThanOneBatch_thenWalkByLastId() {
            // Arrange
            List<SchoolAnalysis> first = rows(1, SchoolAnalysisServiceImpl.BATCH_SIZE);
            List<SchoolAnalysis> second = rows(SchoolAnalysisServiceImpl.BATCH_SIZE + 1, SchoolAnalysisServiceImpl.BATCH_SIZE + 5);
            when(schoolAnalysisRepository.findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(0L)).thenReturn(first);
            when(schoolAnalysisRepository.findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(
                    (long) SchoolAnalysisServiceImpl.BATCH_SIZE)).thenReturn(second);

            // Act
            schoolAnalysisService.calculateAndSaveAllScores(false);

            // Assert: every row is scored, and the short second batch ends the loop
            assertTrue(first.stream().allMatch(row -> row.getSchoolScore() != null));
            assertTrue(second.stream().allMatch(row -> row.getSchoolScore() != null));
            verify(schoolAnalysisRepository, times(2)).findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(anyLong());
            verify(schoolAnalysisRepository, times(2)).saveAll(anyList());
            verify(schoolAnalysisRepository, never()).findTop2000ByIdGreaterThanOrderByIdAsc(anyLong());
        }

        @Test
        @DisplayName("When recalculating existing scores, then read every row, not only the unscored ones")
        void whenRecalculatingExisting_thenReadEveryRow() {
            // Arrange
            SchoolAnalysis scoredBeforeFix = analysis(1L, 300.0, null, 3);
            scoredBeforeFix.setSchoolScore(60.0);
            when(schoolAnalysisRepository.findTop2000ByIdGreaterThanOrderByIdAsc(0L)).thenReturn(List.of(scoredBeforeFix));

            // Act
            schoolAnalysisService.calculateAndSaveAllScores(true);

            // Assert
            assertEquals(75.0, scoredBeforeFix.getSchoolScore(), 1e-9);
            verify(schoolAnalysisRepository, never()).findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(anyLong());
        }

        @Test
        @DisplayName("When nothing needs a score, then save nothing")
        void whenNothingToScore_thenSaveNothing() {
            // Arrange
            when(schoolAnalysisRepository.findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(0L)).thenReturn(List.of());

            // Act
            schoolAnalysisService.calculateAndSaveAllScores(false);

            // Assert
            verify(schoolAnalysisRepository, never()).saveAll(anyList());
        }
    }
}
