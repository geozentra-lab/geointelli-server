package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;

public interface PropertyValuePredictionService {
    PropertyValuePrediction getPropertyValuePredictionById(Long propertyId) throws PropertyValuePredictionNotFoundException;
}
