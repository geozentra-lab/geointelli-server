package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.entity.TransportationAnalysis;
import com.geointelli.ai.property.service.mapper.TransportationAnalysisMapper;
import com.geointelli.ai.property.service.repository.TransportationAnalysisRepository;
import com.geointelli.ai.property.service.service.TransportationAnalysisService;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TransportationAnalysisServiceImpl implements TransportationAnalysisService {
    static final double[] SCORE_WEIGHTS = {0.40, 0.20, 0.20, 0.10, 0.10};
    static final int BATCH_SIZE = 2000;

    private final TransportationAnalysisRepository propertyTransportationAnalysisRepository;
    private final TransportationAnalysisMapper propertyTransportationAnalysisMapper;

    @Override
    public TransportationAnalysisDTO saveScore(TransportationAnalysis analysis) {
        calculateScores(analysis);

        return propertyTransportationAnalysisMapper.toDTO(propertyTransportationAnalysisRepository.save(analysis));
    }

    // unknown values have no part score, so they are left out of the transportation score instead of counting as 0
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

    private Double calculateBusStopDensityScore(Integer busStops) {
        if (busStops == null) {
            return null;
        }
        if (busStops <= 0) {
            return 0.0;
        }

        if (busStops >= 8) {
            return 100.0;
        }

        if (busStops >= 5) {
            return 75.0;
        }

        if (busStops >= 2) {
            return 50.0;
        }

        return 25.0;
    }

    private void calculateScores(TransportationAnalysis analysis) {
        Double metroRailScore = calculateDistanceScore(analysis.getDistMetroRail());
        Double metroMoverScore = calculateDistanceScore(analysis.getDistMetroMover());
        Double busStopDistanceScore = calculateDistanceScore(analysis.getDistBusStop());
        Double highwayScore = calculateDistanceScore(analysis.getDistHighway());
        Double busStopDensityScore = calculateBusStopDensityScore(analysis.getBusStopsWithin500m());

        analysis.setMetroRailScore(metroRailScore);
        analysis.setMetroMoverScore(metroMoverScore);
        analysis.setBusStopDistanceScore(busStopDistanceScore);
        analysis.setBusStopDensityScore(busStopDensityScore);
        analysis.setHighwayScore(highwayScore);
        analysis.setTransportationScore(transportationScore(metroRailScore, metroMoverScore, busStopDensityScore,
                busStopDistanceScore, highwayScore));
    }

    /** Metro rail 40%, metro mover 20%, bus stops within 500 m 20%, bus stop 10%, highway 10%, over the known parts only. */
    static Double transportationScore(Double metroRailScore, Double metroMoverScore, Double busStopDensityScore,
                                      Double busStopDistanceScore, Double highwayScore) {
        return WeightedScore.average(SCORE_WEIGHTS, metroRailScore, metroMoverScore, busStopDensityScore,
                busStopDistanceScore, highwayScore);
    }

    @Override
    @Transactional
    public TransportationAnalysisDTO calculateAndSaveScore(Long propertyId) {
        TransportationAnalysis analysis = propertyTransportationAnalysisRepository.findByPropertyId(propertyId)
                        .orElseThrow(() -> new RuntimeException(
                                "Transportation analysis not found for property: " + propertyId
                        ));

        calculateScores(analysis);
        return propertyTransportationAnalysisMapper.toDTO(propertyTransportationAnalysisRepository.save(analysis));
    }
    
    @Override
    public List<TransportationAnalysisDTO> getPropertyTransportationAnalysisByIds(List<Long> propertyIds) {
        List<Long> uniqueIds = propertyIds.stream().distinct().toList();
        List<TransportationAnalysis> analyses = propertyTransportationAnalysisRepository
                        .findByPropertyIdIn(uniqueIds);
        return analyses.stream().map(propertyTransportationAnalysisMapper::toDTO).toList();
    }

    // walks the rows by id, so saved rows never shift the next batch and rows without any value are not reread
    @Override
    public void calculateAndSaveAllScores(boolean recalculateExisting) {
        long lastId = 0;
        List<TransportationAnalysis> batch;
        do {
            batch = recalculateExisting
                    ? propertyTransportationAnalysisRepository.findTop2000ByIdGreaterThanOrderByIdAsc(lastId)
                    : propertyTransportationAnalysisRepository
                            .findTop2000ByTransportationScoreIsNullAndIdGreaterThanOrderByIdAsc(lastId);
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(this::calculateScores);
            propertyTransportationAnalysisRepository.saveAll(batch);
            propertyTransportationAnalysisRepository.flush();
            lastId = batch.get(batch.size() - 1).getId();
        } while (batch.size() == BATCH_SIZE);
    }
}
