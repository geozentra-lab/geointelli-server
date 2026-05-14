package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PropertyNotFoundException extends ResourceNotFoundException {
    public PropertyNotFoundException(String message) { super(message); }
}
