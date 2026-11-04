package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Optional;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;

public interface SchoolAnalysisService {
    SchoolAnalysisDTO saveScore(SchoolAnalysis analysis);
    List<SchoolAnalysisDTO> getPropertySchoolAnalysisByIds(List<Long> propertyIds);
    Optional<SchoolAnalysisDTO> getPropertySchoolAnalysis(Long propertyId);
    void calculateAndSaveAllScores();
    SchoolAnalysisDTO calculateAndSaveScore(Long propertyId);
}
