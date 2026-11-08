package com.geointelli.ai.property.service.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyIdsRequest;
import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.service.PropertyValuePredictionService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/predictions")
@Slf4j
public class PropertyValuePredictionController {
    private final PropertyValuePredictionService propertyValuePredictionService;

    @GetMapping("/bypropertyid")
    @Operation(summary = "Read a stored property prediction", description = "growthPercentage means projected "
        + "1-year value change: (forecast1y - predictedPrice) / predictedPrice * 100, relative to predictionDate. "
        + "It is null for unsupported valuations or unavailable/nonpositive model values. Negative change is valid. "
        + "It is not growth since the last sale. For display suppression and explanation use the valuation endpoint.")
    ResponseEntity<PropertyValuePredictionDTO> getPropertyValuePredictionById(@RequestParam Long propertyId){
        PropertyValuePredictionDTO propertyValuePrediction = propertyValuePredictionService.getPropertyValuePredictionById(propertyId);
        return ResponseEntity.ok(propertyValuePrediction);
    }
    @PostMapping("/bulk")
    @Operation(summary = "Read stored predictions for multiple properties", description = "Each growthPercentage "
        + "uses the same 1-year horizon from predictionDate: (forecast1y - predictedPrice) / predictedPrice * 100. "
        + "Unsupported valuations or unavailable/nonpositive inputs return null growth, never a zero fallback. "
        + "A real zero means no projected change. This is forecast change, not historical appreciation.")
    ResponseEntity<Map<Long, PropertyValuePredictionDTO>> getPropertyValuePredictionByIds(@RequestBody PropertyIdsRequest propertyIds){
        List<PropertyValuePredictionDTO> predictions = propertyValuePredictionService.getPropertyValuePredictionByIds(propertyIds.getPropertyIds());
        Map<Long, PropertyValuePredictionDTO> result = predictions.stream().collect(Collectors.toMap(PropertyValuePredictionDTO::getPropertyId, Function.identity()));
        return ResponseEntity.ok(result);
    }
}
