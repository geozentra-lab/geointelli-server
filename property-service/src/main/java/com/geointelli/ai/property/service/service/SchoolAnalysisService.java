package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;

public interface SchoolAnalysisService {
    SchoolAnalysisDTO saveScore(SchoolAnalysis analysis);
    List<SchoolAnalysisDTO> getPropertySchoolAnalysisByIds(List<Long> propertyIds);
    void calculateAndSaveAllScores();
    SchoolAnalysisDTO calculateAndSaveScore(Long propertyId);
}
