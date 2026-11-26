package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

/** No listing provider has the requested code (404). */
public class ListingSourceNotFoundException extends ResourceNotFoundException {
    public ListingSourceNotFoundException(String code) {
        super("Listing source not found: " + code);
    }
}
