package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmClientDTO;
import com.geointelli.ai.property.service.dto.CrmClientUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;

/** Manages the current user's contacts. Missing and foreign records both return 404. */
public interface CrmClientService {
    /** Creates a contact; a repeated email within the same owner returns 409. */
    CrmClientDTO create(CrmClientCreateRequest request);
    CrmPageDTO<CrmClientDTO> list(int page, int size);
    CrmClientDTO get(Long id);
    /** Applies only supplied fields; at least one email or phone must remain. */
    CrmClientDTO update(Long id, CrmClientUpdateRequest request);
    /** Deletes an unreferenced contact. Contacts with leads return 409; history is retained. */
    void delete(Long id);
}
