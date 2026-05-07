package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;
import com.geointelli.ai.property.service.mapper.PropertyValuePredictionMapper;
import com.geointelli.ai.property.service.service.PropertyValuePredictionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/prediction")
@Slf4j
public class PropertyValuePredictionController {
    private final PropertyValuePredictionService propertyValuePredictionService;
    private final PropertyValuePredictionMapper propertyValuePredictionMapper;

    @GetMapping("/bypropertyid")
    ResponseEntity<PropertyValuePredictionDTO> getPropertyValuePredictionById(@RequestParam Long propertyId) throws PropertyValuePredictionNotFoundException{
        PropertyValuePrediction propertyValuePrediction = propertyValuePredictionService.getPropertyValuePredictionById(propertyId);
        return ResponseEntity.ok(propertyValuePredictionMapper.toDTO(propertyValuePrediction));
    }
}
