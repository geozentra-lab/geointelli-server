package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmPipelineDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;

/** Tracks manual property deals and counts; closing a deal performs no payment or ownership transfer. */
public interface CrmTransactionService {
    CrmTransactionDTO create(CrmTransactionCreateRequest request);
    CrmPageDTO<CrmTransactionDTO> list(int page, int size, CrmTransactionStage stage);
    CrmTransactionDTO get(Long id);
    /** Validates stage changes under a lock on the affected deal. */
    CrmTransactionDTO update(Long id, CrmTransactionUpdateRequest request);
    CrmPipelineDTO pipeline();
    CrmPageDTO<CrmActivityDTO> history(Long id, int page, int size);
}
