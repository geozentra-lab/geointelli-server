package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.LenderApplication;

/** Shared normalization and consistency checks for manual updates and imported snapshots. */
public interface LenderApplicationValidationService {
    void validate(LenderApplication application);
    String source(String source);
    String externalId(String externalId);
}

