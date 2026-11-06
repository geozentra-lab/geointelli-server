package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LenderApplicationImportRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationImportResult;

/** Bounded partner import with an independent transaction and safe result for each row. */
public interface LenderApplicationImportService {
    LenderApplicationImportResult importApplications(Long actor, Long organizationId, LenderApplicationImportRequest request);
}

