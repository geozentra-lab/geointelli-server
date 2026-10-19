package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.entity.TransportationAnalysis;

public interface TransportationAnalysisService {
    TransportationAnalysisDTO saveScore(TransportationAnalysis analysis);
    List<TransportationAnalysisDTO> getPropertyTransportationAnalysisByIds(List<Long> propertyIds);
    void calculateAndSaveAllScores();
    TransportationAnalysisDTO calculateAndSaveScore(Long propertyId);
}
