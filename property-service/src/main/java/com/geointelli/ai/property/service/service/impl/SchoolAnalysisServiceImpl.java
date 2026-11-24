package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.AssignedSchoolDTO;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;
import com.geointelli.ai.property.service.mapper.SchoolAnalysisMapper;
import com.geointelli.ai.property.service.repository.SchoolAnalysisRepository;
import com.geointelli.ai.property.service.repository.SchoolScoreReader;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

/**
 * School analysis of properties: the v1 score (distances and schools within 1 km, property_school_analysis, kept for
 * one release) and, on read, the assigned schools and school score v2 (SCRUM-203).
 */
@Service
@AllArgsConstructor
public class SchoolAnalysisServiceImpl implements SchoolAnalysisService {
    static final double[] SCORE_WEIGHTS = {0.40, 0.20, 0.40};
    static final int BATCH_SIZE = 2000;
    static final String NEAREST_NOTE = "No attendance zone covers this property for this level: nearest zoned public "
            + "school of the county";
    static final String UNMATCHED_ZONE_NOTE = "Zoned school not in the school data: this level is left out of the "
            + "school score";

    private final SchoolAnalysisRepository schoolAnalysisRepository;
    private final SchoolAnalysisMapper schoolAnalysisMapper;
    private final SchoolScoreReader schoolScoreReader;

    @Override
    public SchoolAnalysisDTO saveScore(SchoolAnalysis analysis) {
        calculateScores(analysis);
        return schoolAnalysisMapper.toDTO(schoolAnalysisRepository.save(analysis));
    }

    // unknown values have no part score, so they are left out of the school score instead of counting as 0
    private Double calculateDistanceScore(Double distance) {
        if (distance == null) {
            return null;
        }

        if (distance <= 500) {
            return 100.0;
        }

        if (distance <= 1000) {
            return 75.0;
        }

        if (distance <= 1500) {
            return 50.0;
        }

        if (distance <= 2000) {
            return 25.0;
        }

        return 0.0;
    }

    private Double calculateSchoolDensityScore(Integer schoolsWithin1km) {
        if (schoolsWithin1km == null) {
            return null;
        }
        if (schoolsWithin1km <= 0) {
            return 0.0;
        }
        if (schoolsWithin1km >= 8) {
            return 100.0;
        }
        if (schoolsWithin1km >= 5) {
            return 75.0;
        }
        if (schoolsWithin1km >= 2) {
            return 50.0;
        }
        return 25.0;
    }

    private void calculateScores(SchoolAnalysis analysis) {
        Double publicSchoolDistanceScore = calculateDistanceScore(analysis.getDistPublicSchool());
        Double charterSchoolDistanceScore = calculateDistanceScore(analysis.getDistCharterSchool());
        Double schoolDensityScore = calculateSchoolDensityScore(analysis.getSchoolsWithin1km());

        analysis.setPublicSchoolDistanceScore(publicSchoolDistanceScore);
        analysis.setCharterSchoolDistanceScore(charterSchoolDistanceScore);
        analysis.setSchoolDensityScore(schoolDensityScore);
        analysis.setSchoolScore(schoolScore(publicSchoolDistanceScore, charterSchoolDistanceScore, schoolDensityScore));
    }

    /** Public distance 40%, charter distance 20%, schools within 1 km 40%, over the known parts only. */
    static Double schoolScore(Double publicSchoolDistanceScore, Double charterSchoolDistanceScore,
                              Double schoolDensityScore) {
        return WeightedScore.average(SCORE_WEIGHTS, publicSchoolDistanceScore, charterSchoolDistanceScore,
                schoolDensityScore);
    }

    @Override
    @Transactional
    public SchoolAnalysisDTO calculateAndSaveScore(Long propertyId) {
        SchoolAnalysis analysis = schoolAnalysisRepository.findByPropertyId(propertyId)
                .orElseThrow(() -> new RuntimeException("School analysis not found for property: " + propertyId));

        calculateScores(analysis);
        return schoolAnalysisMapper.toDTO(schoolAnalysisRepository.save(analysis));
    }

    @Override
    public List<SchoolAnalysisDTO> getPropertySchoolAnalysisByIds(List<Long> propertyIds) {
        List<Long> uniqueIds = propertyIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, SchoolAnalysisDTO> byProperty = new LinkedHashMap<>();
        for (SchoolAnalysis analysis : schoolAnalysisRepository.findByPropertyIdIn(uniqueIds)) {
            byProperty.put(analysis.getPropertyId(), schoolAnalysisMapper.toDTO(analysis));
        }
        addScoreV2(uniqueIds, byProperty);
        return List.copyOf(byProperty.values());
    }

    @Override
    public Optional<SchoolAnalysisDTO> getPropertySchoolAnalysis(Long propertyId) {
        Map<Long, SchoolAnalysisDTO> byProperty = new LinkedHashMap<>();
        schoolAnalysisRepository.findByPropertyId(propertyId)
                .ifPresent(analysis -> byProperty.put(propertyId, schoolAnalysisMapper.toDTO(analysis)));
        addScoreV2(List.of(propertyId), byProperty);
        return Optional.ofNullable(byProperty.get(propertyId));
    }

    /**
     * Adds the assigned schools and school score v2 (SCRUM-203); a property with v2 data but no v1 row gets an
     * analysis with the v1 fields null. Nothing is read before migration V49 is applied.
     */
    private void addScoreV2(List<Long> propertyIds, Map<Long, SchoolAnalysisDTO> byProperty) {
        if (propertyIds.isEmpty() || !schoolScoreReader.available()) {
            return;
        }
        Map<Long, List<SchoolScoreReader.AssignedSchool>> assigned = schoolScoreReader.assignedSchools(propertyIds);
        Map<Long, SchoolScoreReader.Score> scores = schoolScoreReader.scores(propertyIds);
        for (Long propertyId : propertyIds) {
            List<SchoolScoreReader.AssignedSchool> schools = assigned.getOrDefault(propertyId, List.of());
            SchoolScoreReader.Score score = scores.get(propertyId);
            if (schools.isEmpty() && score == null) {
                continue;
            }
            SchoolAnalysisDTO analysis = byProperty.computeIfAbsent(propertyId, id -> {
                SchoolAnalysisDTO created = new SchoolAnalysisDTO();
                created.setPropertyId(id);
                return created;
            });
            analysis.setAssignedSchools(schools.stream().map(SchoolAnalysisServiceImpl::toDTO).toList());
            if (score != null) {
                analysis.setQualityScore(toDouble(score.quality()));
                analysis.setAccessScore(toDouble(score.access()));
                analysis.setChoiceScore(toDouble(score.choice()));
                analysis.setSchoolScoreV2(toDouble(score.school()));
                analysis.setSchoolScoreV2Label(score.label());
                analysis.setGradeYear(score.gradeYear());
                analysis.setScoreVersion(score.scoreVersion());
            }
        }
    }

    static AssignedSchoolDTO toDTO(SchoolScoreReader.AssignedSchool school) {
        AssignedSchoolDTO dto = new AssignedSchoolDTO();
        dto.setLevel(school.level());
        dto.setSchoolId(school.schoolId());
        dto.setName(school.name());
        dto.setSector(school.sector());
        dto.setLetterGrade(school.letterGrade());
        dto.setFldoeGrade(school.fldoeGrade());
        dto.setPctTotalPoints(toDouble(school.pctTotalPoints()));
        dto.setStatewidePercentile(school.statewidePercentile());
        dto.setGradeYear(school.gradeYear());
        dto.setDistanceMeters(toDouble(school.distanceM()));
        dto.setAssignmentMethod(school.method());
        dto.setZoneSourceType(school.zoneSourceType());
        List<String> notes = new ArrayList<>();
        if (AssignedSchoolDTO.METHOD_UNMATCHED_ZONE.equals(school.method())) {
            // no school, so no grade either: say why the level is missing, not that it is ungraded
            dto.setNote(UNMATCHED_ZONE_NOTE);
            return dto;
        }
        if (AssignedSchoolDTO.METHOD_NEAREST.equals(school.method())) {
            notes.add(NEAREST_NOTE);
        }
        if (school.letterGrade() == null) {
            String year = school.gradeYear() == null ? "" : " for " + school.gradeYear();
            notes.add("I".equals(school.fldoeGrade())
                    ? "Not graded by FLDOE" + year + ": incomplete (I), too few students tested"
                    : "Not graded by FLDOE" + year);
        }
        dto.setNote(notes.isEmpty() ? null : String.join(". ", notes));
        return dto;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    // walks the rows by id, so saved rows never shift the next batch and rows without any value are not reread
    @Override
    public void calculateAndSaveAllScores(boolean recalculateExisting) {
        long lastId = 0;
        List<SchoolAnalysis> batch;
        do {
            batch = recalculateExisting
                    ? schoolAnalysisRepository.findTop2000ByIdGreaterThanOrderByIdAsc(lastId)
                    : schoolAnalysisRepository.findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(lastId);
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(this::calculateScores);
            schoolAnalysisRepository.saveAll(batch);
            schoolAnalysisRepository.flush();
            lastId = batch.get(batch.size() - 1).getId();
        } while (batch.size() == BATCH_SIZE);
    }
}
