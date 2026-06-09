package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;

public interface PropertyValuePredictionService {
    PropertyValuePredictionDTO getPropertyValuePredictionById(Long propertyId) throws PropertyValuePredictionNotFoundException;
    List<PropertyValuePredictionDTO> getPropertyValuePredictionByIds(List<Long> propertyIds) throws PropertyValuePredictionNotFoundException;
}
