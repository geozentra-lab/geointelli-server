package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmTaskCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskDTO;
import com.geointelli.ai.property.service.dto.CrmTaskSummaryDTO;
import com.geointelli.ai.property.service.dto.CrmTaskUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;

/** Owns follow-up tasks; no method changes the stage of a linked deal. */
public interface CrmTaskService {
    CrmTaskDTO create(CrmTaskCreateRequest request);
    CrmPageDTO<CrmTaskDTO> list(int page, int size, CrmTaskStatus status);
    CrmTaskDTO get(Long id);
    /** Updates supplied fields; completed/cancelled tasks may reopen to OPEN. */
    CrmTaskDTO update(Long id, CrmTaskUpdateRequest request);
    /** Removes the task only, retaining its history and all linked records. */
    void delete(Long id);
    CrmTaskSummaryDTO summary();
    CrmPageDTO<CrmActivityDTO> history(Long id, int page, int size);
}
