package com.geointelli.ai.property.service.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class PropertyNotFoundException extends Exception {
    private Long propertyId;
}
