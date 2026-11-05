package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationCreateRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationImportRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationImportResult;
import com.geointelli.ai.property.service.dto.LenderApplicationUpdateRequest;
import com.geointelli.ai.property.service.dto.LenderPipelineDTO;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;

/** Manual tracking and partner snapshot processing within an explicitly authorized organization. */
public interface LenderApplicationService {
    LenderApplicationDTO create(Long actor, Long organizationId, LenderApplicationCreateRequest request);
    CrmPageDTO<LenderApplicationDTO> list(Long actor, Long organizationId, int page, int size, LenderApplicationStage stage);
    LenderApplicationDTO get(Long actor, Long organizationId, Long id);
    LenderApplicationDTO update(Long actor, Long organizationId, Long id, LenderApplicationUpdateRequest request);
    LenderPipelineDTO pipeline(Long actor, Long organizationId);
    /** Called by the import orchestrator through a Spring proxy, committing exactly one row. */
    LenderApplicationImportResult.RowResult importRow(Long actor, Long organizationId, String source,
        int rowNumber, LenderApplicationImportRequest.Row row);
}

