package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.UserEntitlementsResponse;

/**
 * Reports effective access using the same realm roles as route authorization.
 */
public interface UserEntitlementService {
    /**
     * Reads the current account's feature grants and visible lender organizations.
     *
     * @return access for the validated bearer token; the legacy persona is not read
     *         only
     */
    UserEntitlementsResponse getEntitlements();
}
