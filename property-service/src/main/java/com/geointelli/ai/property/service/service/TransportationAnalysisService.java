package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Optional;

import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.entity.TransportationAnalysis;

public interface TransportationAnalysisService {
    TransportationAnalysisDTO saveScore(TransportationAnalysis analysis);
    List<TransportationAnalysisDTO> getPropertyTransportationAnalysisByIds(List<Long> propertyIds);
    Optional<TransportationAnalysisDTO> getPropertyTransportationAnalysis(Long propertyId);
    void calculateAndSaveAllScores(boolean recalculateExisting);
    TransportationAnalysisDTO calculateAndSaveScore(Long propertyId);
}
