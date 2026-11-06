package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ContactProfessionalDTO;
import com.geointelli.ai.property.service.dto.ContactResponseDTO;
import com.geointelli.ai.property.service.dto.ContactRequestSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.PropertyContactRequest;

/** The controller resolves the requester from the token before entering the service transaction. */
public interface ContactRequestService {
    ContactRequestSubmissionDTO submit(Long requester, PropertyContactRequest request, String idempotencyKey);
    ContactResponseDTO get(Long actor, Long id);
    CrmPageDTO<ContactProfessionalDTO> professionals(int page, int size);
}

