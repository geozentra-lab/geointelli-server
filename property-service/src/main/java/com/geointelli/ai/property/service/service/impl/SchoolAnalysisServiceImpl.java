package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final SchoolAnalysisRepository schoolAnalysisRepository;
    private final SchoolAnalysisMapper schoolAnalysisMapper;

    @Override
    public SchoolAnalysisDTO saveScore(SchoolAnalysis analysis) {
        calculateScores(analysis);
        return schoolAnalysisMapper.toDTO(schoolAnalysisRepository.save(analysis));
    }

    private Double calculateDistanceScore(Double distance) {
        if (distance == null) {
            return 0.0;
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
        if (schoolsWithin1km == null || schoolsWithin1km <= 0) {
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
        double publicSchoolDistanceScore = calculateDistanceScore(analysis.getDistPublicSchool());
        double charterSchoolDistanceScore = calculateDistanceScore(analysis.getDistCharterSchool());
        double schoolDensityScore = calculateSchoolDensityScore(analysis.getSchoolsWithin1km());

        double schoolScore = publicSchoolDistanceScore * 0.40 + charterSchoolDistanceScore * 0.20
                                + schoolDensityScore * 0.40;

        analysis.setPublicSchoolDistanceScore(publicSchoolDistanceScore);
        analysis.setCharterSchoolDistanceScore(charterSchoolDistanceScore);
        analysis.setSchoolDensityScore(schoolDensityScore);
        analysis.setSchoolScore(schoolScore);
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
    public void calculateAndSaveAllScores() {
        int pageNumber = 0;
        int pageSize = 2000;
        Page<SchoolAnalysis> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.ASC, "id"));
            page = schoolAnalysisRepository.findBySchoolScoreIsNull(pageable);

            List<SchoolAnalysis> analyses = page.getContent();

            for (SchoolAnalysis analysis : analyses) {
                calculateScores(analysis);
            }

            schoolAnalysisRepository.saveAll(analyses);
            schoolAnalysisRepository.flush();

            pageNumber++;
        } while (page.hasNext());
    }
}
