package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.PropertyContact;
import com.geointelli.ai.property.service.entity.User;

/** Internal inbound workflow; ownership is the selected professional, never the submitting user's identity. */
public interface CrmInboundContactService {
    Long createLead(User professional, PropertyContact request);
}

