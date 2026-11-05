package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.entity.LenderOrganization;
import com.geointelli.ai.property.service.entity.LenderOrganizationMember;
import com.geointelli.ai.property.service.repository.LenderOrganizationMemberRepository;
import com.geointelli.ai.property.service.repository.LenderOrganizationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import lombok.RequiredArgsConstructor;

/** Serializes membership changes and application writes for each organization, across application instances. */
@Service
@Transactional
@RequiredArgsConstructor
public class LenderOrganizationServiceImpl implements LenderOrganizationService {
    private final LenderOrganizationRepository organizations;
    private final LenderOrganizationMemberRepository memberships;
    private final UserRepository users;

    @Override
    public LenderOrganizationDTO create(Long actor, String name) {
        if (name == null || name.isBlank() || name.trim().length() > 160)
            throw new IllegalArgumentException("name is required and must be at most 160 characters");
        LenderOrganization organization = new LenderOrganization();
        organization.setName(name.trim());
        organization.setOwnerUserId(actor);
        organizations.saveAndFlush(organization);
        return dto(organization);
    }

    @Override
    public List<LenderOrganizationDTO> list(Long actor) {
        List<LenderOrganizationDTO> result = new ArrayList<>();
        for (LenderOrganization organization : organizations.findAllVisible(actor)) result.add(dto(organization));
        return result;
    }

    @Override
    public List<Long> members(Long actor, Long organizationId) {
        LenderOrganization organization = requireAccess(actor, organizationId, false);
        List<Long> result = new ArrayList<>();
        result.add(organization.getOwnerUserId());
        for (LenderOrganizationMember member : memberships.findByOrganizationIdOrderByUserId(organizationId))
            result.add(member.getUserId());
        return result;
    }

    @Override
    public void addMember(Long actor, Long organizationId, Long userId) {
        requireOwner(actor, organizationId);
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        if (actor.equals(userId) || memberships.existsByOrganizationIdAndUserId(organizationId, userId)) return;
        LenderOrganizationMember member = new LenderOrganizationMember();
        member.setOrganizationId(organizationId);
        member.setUserId(userId);
        memberships.saveAndFlush(member);
    }

    @Override
    public void removeMember(Long actor, Long organizationId, Long userId) {
        LenderOrganization organization = requireOwner(actor, organizationId);
        if (organization.getOwnerUserId().equals(userId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The organization owner cannot be removed");
        memberships.findByOrganizationIdAndUserId(organizationId, userId).ifPresent(memberships::delete);
    }

    @Override
    public LenderOrganization requireAccess(Long actor, Long organizationId, boolean lock) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        if (organizationId == null || organizationId <= 0)
            throw new IllegalArgumentException("organizationId must be positive");
        LenderOrganization organization = organizations.findById(organizationId).orElseThrow(this::notFound);
        verifyMember(actor, organization);
        if (lock) {
            organization = organizations.lockById(organizationId).orElseThrow(this::notFound);
            verifyMember(actor, organization);
        }
        return organization;
    }

    private void verifyMember(Long actor, LenderOrganization organization) {
        if (!organization.getOwnerUserId().equals(actor)
                && !memberships.existsByOrganizationIdAndUserId(organization.getId(), actor)) throw notFound();
    }

    private LenderOrganization requireOwner(Long actor, Long organizationId) {
        LenderOrganization organization = requireAccess(actor, organizationId, true);
        if (!organization.getOwnerUserId().equals(actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the organization owner may manage members");
        return organization;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
    }

    private LenderOrganizationDTO dto(LenderOrganization value) {
        return new LenderOrganizationDTO(value.getId(), value.getName(), value.getOwnerUserId());
    }
}

