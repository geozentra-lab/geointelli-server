package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadDTO;
import com.geointelli.ai.property.service.dto.CrmLeadSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmLeadUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.LeadStage;

/** Manages enquiries linked to the current user's contacts. */
public interface CrmLeadService {
    /** Same owner/key/body replays the existing lead; key reuse with another body returns 409. */
    CrmLeadSubmissionDTO create(CrmLeadCreateRequest request, String idempotencyKey);
    CrmPageDTO<CrmLeadDTO> list(int page, int size, LeadStage stage);
    CrmLeadDTO get(Long id);
    /** Applies supported field edits and validates forward stage transitions under a row lock. */
    CrmLeadDTO update(Long id, CrmLeadUpdateRequest request);
}
