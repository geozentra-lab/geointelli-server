package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

/** The property, sale or assessment a data-quality reference points to does not exist (404, SCRUM-190). */
public class DataQualityEntityNotFoundException extends ResourceNotFoundException {
    public DataQualityEntityNotFoundException(String message) {
        super(message);
    }
}
