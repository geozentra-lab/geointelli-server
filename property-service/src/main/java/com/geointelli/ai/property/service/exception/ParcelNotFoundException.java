package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ParcelNotFoundException extends ResourceNotFoundException {
    public ParcelNotFoundException(String message) { super(message); }
}
