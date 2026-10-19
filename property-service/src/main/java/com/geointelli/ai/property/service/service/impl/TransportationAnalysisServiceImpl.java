package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private final TransportationAnalysisRepository propertyTransportationAnalysisRepository;
    private final TransportationAnalysisMapper propertyTransportationAnalysisMapper;

    @Override
    public TransportationAnalysisDTO saveScore(TransportationAnalysis analysis) {
        calculateScores(analysis);

        return propertyTransportationAnalysisMapper.toDTO(propertyTransportationAnalysisRepository.save(analysis));
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

    private Double calculateBusStopDensityScore(Integer busStops) {
        if (busStops == null || busStops <= 0) {
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
        double metroRailScore = calculateDistanceScore(analysis.getDistMetroRail());
        double metroMoverScore = calculateDistanceScore(analysis.getDistMetroMover());
        double busStopDistanceScore = calculateDistanceScore(analysis.getDistBusStop());
        double highwayScore = calculateDistanceScore(analysis.getDistHighway());
        double busStopDensityScore = calculateBusStopDensityScore(analysis.getBusStopsWithin500m());
        double transportationScore = metroRailScore * 0.40 + metroMoverScore * 0.20
                + busStopDensityScore * 0.20
                + busStopDistanceScore * 0.10
                + highwayScore * 0.10;

        analysis.setMetroRailScore(metroRailScore);
        analysis.setMetroMoverScore(metroMoverScore);
        analysis.setBusStopDistanceScore(busStopDistanceScore);
        analysis.setBusStopDensityScore(busStopDensityScore);
        analysis.setHighwayScore(highwayScore);
        analysis.setTransportationScore(transportationScore);
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

    @Override
    // @Transactional
    public void calculateAndSaveAllScores() {
        int pageNumber = 0;
        int pageSize = 2000;
        Page<TransportationAnalysis> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize,Sort.by(Sort.Direction.ASC, "id"));
            page = propertyTransportationAnalysisRepository.findByTransportationScoreIsNull(pageable);
            List<TransportationAnalysis> analyses = page.getContent();
            for (TransportationAnalysis analysis : analyses) {
                calculateScores(analysis);
            }
            propertyTransportationAnalysisRepository.saveAll(analyses);
            propertyTransportationAnalysisRepository.flush();
            pageNumber++;

        } while (page.hasNext());
    }
}
