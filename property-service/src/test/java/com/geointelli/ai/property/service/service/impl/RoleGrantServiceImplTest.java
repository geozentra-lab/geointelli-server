package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.RealtorLicenseExpiryRunDTO;
import com.geointelli.ai.property.service.entity.RoleGrantAction;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;
import com.geointelli.ai.property.service.entity.RoleGrantSource;
import com.geointelli.ai.property.service.entity.RoleRequest;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;
import com.geointelli.ai.property.service.repository.RoleGrantEventRepository;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.KeycloakRoleService;

/** Keycloak first, history second (SCRUM-204). */
class RoleGrantServiceImplTest {

    private final KeycloakRoleService keycloak = mock(KeycloakRoleService.class);
    private final RoleGrantEventRepository events = mock(RoleGrantEventRepository.class);
    private final RoleRequestRepository requests = mock(RoleRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private RoleGrantServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new RoleGrantServiceImpl(keycloak, events, requests, users, currentUser, transactions);
        user = new User();
        user.setId(7L);
        user.setKeycloakUserId("kc-7");
        User admin = new User();
        admin.setId(1L);
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("A grant calls Keycloak, then records who, which role, why, who decided")
    void grantRecordsAfterKeycloak() {
        RoleGrantEvent event = service.grant(user, "lender", RoleGrantSource.ROLE_REQUEST, 50L, "ok", 1L);

        var order = inOrder(keycloak, events);
        order.verify(keycloak).grantRealmRole("kc-7", "lender");
        order.verify(events).save(event);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getAction()).isEqualTo(RoleGrantAction.GRANT);
        assertThat(event.getSource()).isEqualTo(RoleGrantSource.ROLE_REQUEST);
        assertThat(event.getSourceId()).isEqualTo(50L);
        assertThat(event.getDecidedByUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("When Keycloak fails nothing is recorded")
    void keycloakFailureRecordsNothing() {
        doThrow(KeycloakRoleException.unavailable("down")).when(keycloak).grantRealmRole(any(), any());

        assertThatThrownBy(() -> service.grant(user, "lender", RoleGrantSource.ROLE_REQUEST, 50L, "ok", 1L))
            .isInstanceOf(KeycloakRoleException.class);
        verify(events, never()).save(any());
    }

    @Test
    @DisplayName("An admin revocation removes the role, records it and marks the approved request REVOKED")
    void adminRevocation() {
        RoleRequest approved = new RoleRequest();
        approved.setId(50L);
        approved.setStatus(RoleRequestStatus.APPROVED);
        when(requests.findFirstByUserIdAndRequestedRoleAndStatusOrderByIdDesc(7L, "lender", RoleRequestStatus.APPROVED))
            .thenReturn(Optional.of(approved));

        var event = service.revokeByAdmin(7L, " Lender ", " Organization closed ");

        verify(keycloak).revokeRealmRole("kc-7", "lender");
        assertThat(event.action()).isEqualTo(RoleGrantAction.REVOKE);
        assertThat(event.source()).isEqualTo(RoleGrantSource.ADMIN_REVOCATION);
        assertThat(event.sourceId()).isEqualTo(50L);
        assertThat(event.reason()).isEqualTo("Organization closed");
        assertThat(event.decidedByUserId()).isEqualTo(1L);
        assertThat(approved.getStatus()).isEqualTo(RoleRequestStatus.REVOKED);
    }

    @Test
    @DisplayName("Revocation refuses admin and unknown roles (400), a missing reason (400) and unknown users (404)")
    void revocationValidation() {
        assertThatThrownBy(() -> service.revokeByAdmin(7L, "admin", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.revokeByAdmin(7L, "lender", " ")).isInstanceOf(IllegalArgumentException.class);
        when(users.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revokeByAdmin(99L, "lender", "x"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("Expiry run revokes each candidate on its own; one Keycloak failure does not stop the others")
    void expiryRun() {
        User other = new User();
        other.setId(8L);
        other.setKeycloakUserId("kc-8");
        when(users.findById(8L)).thenReturn(Optional.of(other));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(events.findRealtorGrantsWithoutValidLicense(today)).thenReturn(List.of(7L, 8L));
        doThrow(KeycloakRoleException.unavailable("down")).when(keycloak).revokeRealmRole("kc-8", "realtor");

        RealtorLicenseExpiryRunDTO run = service.revokeExpiredRealtorGrants();

        assertThat(run).isEqualTo(new RealtorLicenseExpiryRunDTO(today, 2, 1, 1));
        ArgumentCaptor<RoleGrantEvent> saved = ArgumentCaptor.forClass(RoleGrantEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getSource()).isEqualTo(RoleGrantSource.LICENSE_EXPIRY);
        assertThat(saved.getValue().getDecidedByUserId()).isNull();
        assertThat(saved.getValue().getReason()).contains(today.toString());
    }
}
