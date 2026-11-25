package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.entity.LenderOrganization;
import com.geointelli.ai.property.service.mapper.LenderApplicationMapper;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.repository.LenderOrganizationMemberRepository;
import com.geointelli.ai.property.service.repository.LenderOrganizationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.LenderApplicationValidationService;

/**
 * Real membership and application services prove role grants cannot bypass
 * organization boundaries.
 */
class LenderOwnershipBoundaryTest {
    private final LenderOrganizationRepository organizations = mock(LenderOrganizationRepository.class);
    private final LenderOrganizationMemberRepository members = mock(LenderOrganizationMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LenderApplicationRepository applications = mock(LenderApplicationRepository.class);
    private final LenderOrganizationServiceImpl membership = new LenderOrganizationServiceImpl(organizations, members,
            users);
    private final LenderApplicationServiceImpl service = new LenderApplicationServiceImpl(applications, membership,
            mock(LenderApplicationValidationService.class), mock(LenderApplicationMapper.class));

    @BeforeEach
    void organization() {
        LenderOrganization organization = new LenderOrganization();
        organization.setId(10L);
        organization.setOwnerUserId(7L);
        when(organizations.findById(10L)).thenReturn(Optional.of(organization));
        when(organizations.lockById(10L)).thenReturn(Optional.of(organization));
    }

    private void forbiddenResource(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }

    @Test
    void outsiderCannotReadListUpdateOrCreateInAnotherOrganization() {
        forbiddenResource(() -> service.get(8L, 10L, 55L), 404);
        forbiddenResource(() -> service.list(8L, 10L, 0, 20, null), 404);
        forbiddenResource(() -> service.update(8L, 10L, 55L, null), 404);
        forbiddenResource(() -> service.create(8L, 10L, null), 404);
        verifyNoInteractions(applications);
    }

    @Test
    void validOrganizationCannotReadOrUpdateAnotherOrganizationsApplication() {
        forbiddenResource(() -> service.get(7L, 10L, 55L), 404);
        forbiddenResource(() -> service.update(7L, 10L, 55L, null), 404);
        verify(applications, times(2)).findByIdAndOrganizationId(55L, 10L);
        verifyNoMoreInteractions(applications);
    }

    @Test
    void ordinaryMemberCannotManageMembers() {
        when(members.existsByOrganizationIdAndUserId(10L, 8L)).thenReturn(true);
        forbiddenResource(() -> membership.addMember(8L, 10L, 9L), 403);
        forbiddenResource(() -> membership.removeMember(8L, 10L, 9L), 403);
        verifyNoInteractions(users);
        verify(members, never()).saveAndFlush(any());
        verify(members, never()).delete(any());
    }
}
