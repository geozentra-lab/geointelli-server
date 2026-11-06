package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmClient;
import com.geointelli.ai.property.service.entity.CrmLead;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.PropertyContact;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmInboundContactService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import lombok.RequiredArgsConstructor;

/** Reuses CRM validation, entities and activity records in the submission's atomic transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class CrmInboundContactServiceImpl implements CrmInboundContactService {
    private final CrmClientRepository clients;
    private final CrmLeadRepository leads;
    private final CrmValidationService validation;
    private final CrmActivityService activity;

    @Override
    public Long createLead(User professional, PropertyContact request) {
        CrmClient client = request.getContactEmail() == null ? null
            : clients.findByUser_IdAndEmail(professional.getId(), request.getContactEmail()).orElse(null);
        if (client == null) {
            validation.validate(new CrmClientCreateRequest(request.getContactName(), request.getContactEmail(),
                request.getContactPhone(), null));
            client = new CrmClient();
            client.setUser(professional);
            client.setName(request.getContactName());
            client.setEmail(request.getContactEmail());
            client.setPhone(request.getContactPhone());
            clients.saveAndFlush(client);
            activity.record(professional.getId(), client.getId(), CrmSubjectType.CLIENT, client.getId(),
                CrmActivityType.CLIENT_CREATED, "Client created from a consented property contact request");
        } else {
            client = clients.findOwnedForUpdate(client.getId(), professional.getId()).orElseThrow();
        }
        String title = "Property contact request #" + request.getId();
        validation.validate(new CrmLeadCreateRequest(client.getId(), request.getPropertyId(), title,
            "PROPERTY_CONTACT_REQUEST", request.getMessage()));
        CrmLead lead = new CrmLead();
        lead.setUser(professional);
        lead.setClient(client);
        lead.setPropertyId(request.getPropertyId());
        lead.setTitle(title);
        lead.setSource("PROPERTY_CONTACT_REQUEST");
        lead.setNotes(request.getMessage());
        lead.setRequestKey("contact-request:" + request.getId());
        lead.setRequestHash(request.getRequestHash());
        leads.saveAndFlush(lead);
        activity.record(professional.getId(), client.getId(), CrmSubjectType.LEAD, lead.getId(),
            CrmActivityType.LEAD_CREATED, "Lead created from property contact request #" + request.getId());
        return lead.getId();
    }
}

