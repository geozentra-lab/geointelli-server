package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.PropertyValuePredictionService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Data
@Slf4j
public class PropertyValuePredictionServiceImpl implements PropertyValuePredictionService {
    private final PropertyValuePredictionRepository propertyValuePredictionRepository;

    @Override
    public PropertyValuePrediction getPropertyValuePredictionById(Long propertyId) throws PropertyValuePredictionNotFoundException{
        return propertyValuePredictionRepository.findByPropertyId(propertyId)
                .orElseThrow(() -> new PropertyValuePredictionNotFoundException("prrediction value not found for property with id : " + propertyId));
    }
}
