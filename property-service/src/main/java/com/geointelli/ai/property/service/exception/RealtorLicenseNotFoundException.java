package com.geointelli.ai.property.service.exception;

import com.geointelli.ai.property.service.exception.base.ResourceNotFoundException;

/** The current user has no realtor license submission yet (404). */
public class RealtorLicenseNotFoundException extends ResourceNotFoundException {
    public RealtorLicenseNotFoundException(String message) { super(message); }
}
