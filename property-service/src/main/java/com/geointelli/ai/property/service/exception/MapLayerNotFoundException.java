package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;
/**
 * Thrown when a requested map layer is unknown or not supported (mapped to 404).
 */
public class MapLayerNotFoundException extends ResourceNotFoundException {
    public MapLayerNotFoundException(String message) {
        super(message);
    }
}
