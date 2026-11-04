package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;
import com.geointelli.ai.property.service.mapper.SchoolAnalysisMapper;
import com.geointelli.ai.property.service.repository.SchoolAnalysisRepository;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SchoolAnalysisServiceImpl implements SchoolAnalysisService {
    static final double[] SCORE_WEIGHTS = {0.40, 0.20, 0.40};
    static final int BATCH_SIZE = 2000;

    private final SchoolAnalysisRepository schoolAnalysisRepository;
    private final SchoolAnalysisMapper schoolAnalysisMapper;

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
        List<Long> uniqueIds = propertyIds.stream().distinct().toList();
        List<SchoolAnalysis> analyses = schoolAnalysisRepository.findByPropertyIdIn(uniqueIds);
        return analyses.stream().map(schoolAnalysisMapper::toDTO).toList();
    }

    @Override
    public Optional<SchoolAnalysisDTO> getPropertySchoolAnalysis(Long propertyId) {
        return schoolAnalysisRepository.findByPropertyId(propertyId).map(schoolAnalysisMapper::toDTO);
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
