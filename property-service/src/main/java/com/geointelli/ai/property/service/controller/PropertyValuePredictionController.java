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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/predictions")
@Slf4j
public class PropertyValuePredictionController {
    private final PropertyValuePredictionService propertyValuePredictionService;

    @GetMapping("/bypropertyid")
    ResponseEntity<PropertyValuePredictionDTO> getPropertyValuePredictionById(@RequestParam Long propertyId){
        PropertyValuePredictionDTO propertyValuePrediction = propertyValuePredictionService.getPropertyValuePredictionById(propertyId);
        return ResponseEntity.ok(propertyValuePrediction);
    }
    @PostMapping("/bulk")
    ResponseEntity<Map<Long, PropertyValuePredictionDTO>> getPropertyValuePredictionByIds(@RequestBody PropertyIdsRequest propertyIds){
        List<PropertyValuePredictionDTO> predictions = propertyValuePredictionService.getPropertyValuePredictionByIds(propertyIds.getPropertyIds());
        Map<Long, PropertyValuePredictionDTO> result = predictions.stream().collect(Collectors.toMap(PropertyValuePredictionDTO::getPropertyId, Function.identity()));
        return ResponseEntity.ok(result);
    }
}
