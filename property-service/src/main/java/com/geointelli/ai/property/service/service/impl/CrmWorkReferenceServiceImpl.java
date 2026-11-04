package com.geointelli.ai.property.service.service.impl;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmWorkLinks;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.repository.CrmTransactionRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CrmWorkReferenceService;
import lombok.RequiredArgsConstructor;

/** Immutable reference snapshots never grant access to another user's CRM records. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CrmWorkReferenceServiceImpl implements CrmWorkReferenceService {
    private final CrmClientRepository clients;
    private final CrmLeadRepository leads;
    private final CrmTransactionRepository transactions;
    private final PropertyRepository properties;

    @Override
    public CrmWorkLinks resolve(Long owner, Long clientId, Long leadId, Long propertyId, Long transactionId) {
        Long transactionLeadId = null;
        if (clientId != null) {
            clients.findByIdAndUser_Id(clientId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        }
        if (transactionId != null) {
            var transaction = transactions.findByIdAndUserId(transactionId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
            clientId = consistent(clientId, transaction.getClientId(), "clientId");
            propertyId = consistent(propertyId, transaction.getPropertyId(), "propertyId");
            leadId = consistent(leadId, transaction.getLeadId(), "leadId");
            transactionLeadId = transaction.getLeadId();
        }
        if (leadId != null) {
            var lead = leads.findByIdAndUser_Id(leadId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
            clientId = consistent(clientId, lead.getClient().getId(), "clientId");
            // A deal retains the lead/property snapshot from its creation, even if that lead is edited later.
            if (transactionId == null || !Objects.equals(leadId, transactionLeadId)) {
                propertyId = consistent(propertyId, lead.getPropertyId(), "propertyId");
            }
        }
        if (clientId != null) {
            clients.findOwnedForUpdate(clientId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        }
        if (propertyId != null && !properties.existsById(propertyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        }
        return new CrmWorkLinks(clientId, leadId, propertyId, transactionId);
    }

    private Long consistent(Long supplied, Long related, String name) {
        if (supplied != null && related != null && !Objects.equals(supplied, related)) {
            throw new IllegalArgumentException(name + " does not match the related CRM record");
        }
        return supplied == null ? related : supplied;
    }
}
