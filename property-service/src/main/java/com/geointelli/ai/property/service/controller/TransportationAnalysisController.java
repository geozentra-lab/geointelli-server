package com.geointelli.ai.property.service.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyIdsRequest;
import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.service.TransportationAnalysisService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/property-transportation-analysis")
@RequiredArgsConstructor
public class TransportationAnalysisController {
    private final TransportationAnalysisService
            propertyTransportationAnalysisService;

    @PostMapping("/bulk")
    public ResponseEntity<Map<Long, TransportationAnalysisDTO>> getPropertyTransportationAnalysisByIds(
            @RequestBody PropertyIdsRequest propertyIds) {
        List<TransportationAnalysisDTO> analyses = propertyTransportationAnalysisService
                        .getPropertyTransportationAnalysisByIds(propertyIds.getPropertyIds());
        Map<Long, TransportationAnalysisDTO> result = analyses.stream()
                        .collect(Collectors.toMap(TransportationAnalysisDTO::getPropertyId,
                                Function.identity()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/calculate-all")
    public ResponseEntity<Void> calculateAndSaveAllScores() {
        propertyTransportationAnalysisService.calculateAndSaveAllScores();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("{id}/calculate-property-score")
    public ResponseEntity<TransportationAnalysisDTO> calculateAndSavePropertyScore(@PathVariable Long id) {
        TransportationAnalysisDTO result = propertyTransportationAnalysisService
                        .calculateAndSaveScore(id);
        return ResponseEntity.ok(result);
    }
}
