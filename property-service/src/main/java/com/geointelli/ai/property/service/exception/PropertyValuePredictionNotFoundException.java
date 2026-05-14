package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PropertyValuePredictionNotFoundException extends ResourceNotFoundException {
    public PropertyValuePredictionNotFoundException(String message) { super(message); }
}
