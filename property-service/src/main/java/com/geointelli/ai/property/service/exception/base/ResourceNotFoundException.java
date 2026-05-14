package com.geointelli.ai.property.service.exception.base;

public abstract class ResourceNotFoundException extends RuntimeException {
    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
