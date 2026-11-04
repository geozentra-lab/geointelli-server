package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.CrmWorkLinks;

/** Reuses CRM ownership lookups and rejects inconsistent links before writing work records. */
public interface CrmWorkReferenceService {
    /** Resolves optional related IDs within an existing transaction, locking the resulting contact against deletion. */
    CrmWorkLinks resolve(Long ownerId, Long clientId, Long leadId, Long propertyId, Long transactionId);
}
