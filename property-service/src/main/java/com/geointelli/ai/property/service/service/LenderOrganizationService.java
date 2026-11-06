package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.entity.LenderOrganization;

/** Explicit organization access; actor IDs are resolved from the authenticated token before starting a transaction. */
public interface LenderOrganizationService {
    LenderOrganizationDTO create(Long actor, String name);
    List<LenderOrganizationDTO> list(Long actor);
    List<Long> members(Long actor, Long organizationId);
    void addMember(Long actor, Long organizationId, Long userId);
    void removeMember(Long actor, Long organizationId, Long userId);
    /** Verifies membership. Writers lock the organization and recheck access until their transaction commits. */
    LenderOrganization requireAccess(Long actor, Long organizationId, boolean lock);
}

