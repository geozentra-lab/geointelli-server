package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmNoteRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;

/** Records transactional changes and user notes; no public endpoint can forge system events. */
public interface CrmActivityService {
    /** Internal only: caller must already be in the authorized record's transaction. */
    void record(Long ownerId, Long clientId, CrmSubjectType subjectType, Long subjectId,
        CrmActivityType type, String description);
    CrmPageDTO<CrmActivityDTO> list(int page, int size);
    /** Client history also includes its leads' events. */
    CrmPageDTO<CrmActivityDTO> clientHistory(Long clientId, int page, int size);
    CrmPageDTO<CrmActivityDTO> leadHistory(Long leadId, int page, int size);
    CrmActivityDTO addClientNote(Long clientId, CrmNoteRequest request);
    CrmActivityDTO addLeadNote(Long leadId, CrmNoteRequest request);
}
