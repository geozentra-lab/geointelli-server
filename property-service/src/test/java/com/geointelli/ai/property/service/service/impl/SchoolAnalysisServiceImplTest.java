package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AssignedSchoolDTO;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;
import com.geointelli.ai.property.service.mapper.SchoolAnalysisMapper;
import com.geointelli.ai.property.service.repository.SchoolAnalysisRepository;
import com.geointelli.ai.property.service.repository.SchoolScoreReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class SchoolAnalysisServiceImplTest {

    SchoolAnalysisRepository schoolAnalysisRepository = mock(SchoolAnalysisRepository.class);
    SchoolAnalysisMapper schoolAnalysisMapper = mock(SchoolAnalysisMapper.class);
    SchoolScoreReader schoolScoreReader = mock(SchoolScoreReader.class);

    SchoolAnalysisServiceImpl schoolAnalysisService =
            new SchoolAnalysisServiceImpl(schoolAnalysisRepository, schoolAnalysisMapper, schoolScoreReader);

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

    @Nested
    @DisplayName("Read With Score V2 Tests")
    class ReadWithScoreV2 {

        private SchoolScoreReader.AssignedSchool school(long propertyId, String level, String method, String letter,
                                                        String fldoeGrade, Integer percentile) {
            return new SchoolScoreReader.AssignedSchool(propertyId, level, 7L, "CORAL WAY K-8 CENTER", "PUBLIC", method,
                    "ZONE".equals(method) ? "OFFICIAL" : null, new BigDecimal("640.2"), 2026, letter, fldoeGrade,
                    percentile == null ? null : new BigDecimal("69"), percentile);
        }

        @Test
        @DisplayName("When a property has v2 data but no v1 row, then it is returned with the v1 fields null")
        void whenOnlyV2_thenAnalysisWithV2Fields() {
            // Arrange: property 1 has a v1 row, property 2 only assignments and a score
            SchoolAnalysis v1 = analysis(1L, 300.0, 1200.0, 3);
            SchoolAnalysisDTO v1Dto = new SchoolAnalysisDTO();
            v1Dto.setPropertyId(1L);
            v1Dto.setSchoolScore(70.0);
            when(schoolAnalysisRepository.findByPropertyIdIn(List.of(1L, 2L))).thenReturn(List.of(v1));
            when(schoolAnalysisMapper.toDTO(v1)).thenReturn(v1Dto);
            when(schoolScoreReader.available()).thenReturn(true);
            when(schoolScoreReader.assignedSchools(List.of(1L, 2L))).thenReturn(Map.of(2L,
                    List.of(school(2L, "ELEMENTARY", "ZONE", "A", "A", 83))));
            when(schoolScoreReader.scores(List.of(1L, 2L))).thenReturn(Map.of(2L, new SchoolScoreReader.Score(2L,
                    new BigDecimal("83.00"), new BigDecimal("100.00"), null, new BigDecimal("87.13"), "Excellent", 2026, 2,
                    null)));

            // Act
            List<SchoolAnalysisDTO> analyses = schoolAnalysisService.getPropertySchoolAnalysisByIds(List.of(1L, 2L, 2L));

            // Assert
            assertEquals(2, analyses.size());
            assertEquals(70.0, analyses.get(0).getSchoolScore());
            assertNull(analyses.get(0).getSchoolScoreV2());
            SchoolAnalysisDTO v2Only = analyses.get(1);
            assertEquals(2L, v2Only.getPropertyId());
            assertNull(v2Only.getSchoolScore());
            assertEquals(87.13, v2Only.getSchoolScoreV2());
            assertEquals("Excellent", v2Only.getSchoolScoreV2Label());
            assertEquals(83.0, v2Only.getQualityScore());
            assertNull(v2Only.getChoiceScore());
            assertEquals(2026, v2Only.getGradeYear());
            assertEquals(2, v2Only.getScoreVersion());
            AssignedSchoolDTO assigned = v2Only.getAssignedSchools().get(0);
            assertEquals("ELEMENTARY", assigned.getLevel());
            assertEquals("A", assigned.getLetterGrade());
            assertEquals(83, assigned.getStatewidePercentile());
            assertEquals(640.2, assigned.getDistanceMeters());
            assertEquals("ZONE", assigned.getAssignmentMethod());
            assertEquals("OFFICIAL", assigned.getZoneSourceType());
            assertNull(assigned.getNote());
        }

        @Test
        @DisplayName("When an assigned school is not graded or is the nearest one, then its note says so")
        void whenNotGradedOrNearest_thenNote() {
            // Arrange
            when(schoolAnalysisRepository.findByPropertyId(5L)).thenReturn(Optional.empty());
            when(schoolScoreReader.available()).thenReturn(true);
            when(schoolScoreReader.assignedSchools(List.of(5L))).thenReturn(Map.of(5L, List.of(
                    school(5L, "ELEMENTARY", "ZONE", null, "I", null),
                    school(5L, "HIGH", "NEAREST", null, null, null))));
            when(schoolScoreReader.scores(List.of(5L))).thenReturn(Map.of());

            // Act
            SchoolAnalysisDTO analysis = schoolAnalysisService.getPropertySchoolAnalysis(5L).orElseThrow();

            // Assert: no grade is never a low grade
            assertEquals("Not graded by FLDOE for 2026: incomplete (I), too few students tested",
                    analysis.getAssignedSchools().get(0).getNote());
            assertEquals(SchoolAnalysisServiceImpl.NEAREST_NOTE + ". Not graded by FLDOE for 2026",
                    analysis.getAssignedSchools().get(1).getNote());
            assertNull(analysis.getAssignedSchools().get(0).getStatewidePercentile());
            assertNull(analysis.getSchoolScoreV2());
        }

        @Test
        @DisplayName("When the zoned school is not in the school data, then the level shows the zone's name and says why")
        void whenZonedSchoolUnmatched_thenNoteWithoutGrade() {
            // Arrange: the elementary zone names a school we do not have
            when(schoolAnalysisRepository.findByPropertyId(6L)).thenReturn(Optional.empty());
            when(schoolScoreReader.available()).thenReturn(true);
            when(schoolScoreReader.assignedSchools(List.of(6L))).thenReturn(Map.of(6L, List.of(
                    new SchoolScoreReader.AssignedSchool(6L, "ELEMENTARY", null, "Shadowlawn Elementary", null,
                            "UNMATCHED_ZONE", "OFFICIAL", null, 2026, null, null, null, null))));
            when(schoolScoreReader.scores(List.of(6L))).thenReturn(Map.of());

            // Act
            AssignedSchoolDTO school = schoolAnalysisService.getPropertySchoolAnalysis(6L).orElseThrow()
                    .getAssignedSchools().get(0);

            // Assert: not "not graded": there is no school to grade
            assertNull(school.getSchoolId());
            assertEquals("Shadowlawn Elementary", school.getName());
            assertEquals(AssignedSchoolDTO.METHOD_UNMATCHED_ZONE, school.getAssignmentMethod());
            assertNull(school.getDistanceMeters());
            assertEquals(SchoolAnalysisServiceImpl.UNMATCHED_ZONE_NOTE, school.getNote());
        }

        @Test
        @DisplayName("When migration V49 is not applied, then only the v1 row is read")
        void whenV49Missing_thenV1Only() {
            // Arrange
            when(schoolAnalysisRepository.findByPropertyId(9L)).thenReturn(Optional.empty());
            when(schoolScoreReader.available()).thenReturn(false);

            // Act + Assert
            assertTrue(schoolAnalysisService.getPropertySchoolAnalysis(9L).isEmpty());
            verify(schoolScoreReader, never()).assignedSchools(anyList());
        }
    }
}
