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
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/property-school-analysis")
@RequiredArgsConstructor
public class SchoolAnalysisController {
    private final SchoolAnalysisService
            propertySchoolAnalysisService;

    @PostMapping("/bulk")
    public ResponseEntity<Map<Long, SchoolAnalysisDTO>> getPropertySchoolAnalysisByIds(
            @RequestBody PropertyIdsRequest propertyIds) {
        List<SchoolAnalysisDTO> analyses = propertySchoolAnalysisService
                        .getPropertySchoolAnalysisByIds(propertyIds.getPropertyIds());
        Map<Long, SchoolAnalysisDTO> result = analyses.stream()
                        .collect(Collectors.toMap(SchoolAnalysisDTO::getPropertyId,
                                Function.identity()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/calculate-all")
    public ResponseEntity<Void> calculateAndSaveAllScores() {
        propertySchoolAnalysisService.calculateAndSaveAllScores();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("{id}/calculate-property-score")
    public ResponseEntity<SchoolAnalysisDTO> calculateAndSavePropertyScore(@PathVariable Long id) {
        SchoolAnalysisDTO result = propertySchoolAnalysisService.calculateAndSaveScore(id);
        return ResponseEntity.ok(result);
    }
}
