package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.LenderMemberLookupDTO;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.entity.LenderOrganization;

/** Explicit organization access; actor IDs are resolved from the authenticated token before starting a transaction. */
public interface LenderOrganizationService {
    LenderOrganizationDTO create(Long actor, String name);
    List<LenderOrganizationDTO> list(Long actor);
    List<Long> members(Long actor, Long organizationId);
    void addMember(Long actor, Long organizationId, Long userId);
    /**
     * Owner only: the account that uses this email (ignoring case), so the owner can add it as a member. 400 for a
     * missing or malformed email, 403 for a member who is not the owner, 404 for an outsider or when no account uses
     * the email.
     */
    LenderMemberLookupDTO lookupMember(Long actor, Long organizationId, String email);
    /**
     * Owner only: suggestions while the owner types, so they can add a member. The same accounts in the same order as
     * the admin search ({@link UserService#search}: email, full name or last name starts with {@code q}, case
     * ignored, newest row of each email only), each with {@code member} true when it is already the owner or a
     * member. 400 for {@code q} under 2 or over 254 characters or {@code size} outside 1 to 25, 403 for a member who is
     * not the owner, 404 for an outsider.
     */
    List<LenderMemberLookupDTO> searchMembers(Long actor, Long organizationId, String q, int size);
    void removeMember(Long actor, Long organizationId, Long userId);
    /** Verifies membership. Writers lock the organization and recheck access until their transaction commits. */
    LenderOrganization requireAccess(Long actor, Long organizationId, boolean lock);
}

