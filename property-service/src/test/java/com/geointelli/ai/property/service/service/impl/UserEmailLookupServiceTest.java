package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.LenderMemberLookupDTO;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.LenderOrganization;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.UserMapper;
import com.geointelli.ai.property.service.repository.LenderOrganizationMemberRepository;
import com.geointelli.ai.property.service.repository.LenderOrganizationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;

/** Finding an account by email: for the admin, and for the owner of a lender organization who adds a member. */
class UserEmailLookupServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserMapper mapper = mock(UserMapper.class);
    private final LenderOrganizationRepository organizations = mock(LenderOrganizationRepository.class);
    private final LenderOrganizationMemberRepository members = mock(LenderOrganizationMemberRepository.class);
    private final UserServiceImpl userService = new UserServiceImpl(users, mapper);
    private final LenderOrganizationServiceImpl organizationService =
        new LenderOrganizationServiceImpl(organizations, members, users);
    private final User colleague = new User();

    @BeforeEach
    void data() {
        colleague.setId(9L);
        colleague.setEmail("colleague@bank.example");
        colleague.setFirstName("Cleo");
        colleague.setLastName("Banks");
        LenderOrganization organization = new LenderOrganization();
        organization.setId(10L);
        organization.setOwnerUserId(7L);
        when(organizations.findById(10L)).thenReturn(Optional.of(organization));
    }

    private static int status(Throwable error) {
        return ((ResponseStatusException) error).getStatusCode().value();
    }

    @Test
    @DisplayName("Admin: the trimmed email is looked up and the account mapped")
    void adminFindsAccount() {
        UserDTO dto = new UserDTO();
        dto.setId(9L);
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("Colleague@Bank.example")).thenReturn(Optional.of(colleague));
        when(mapper.toDTO(colleague)).thenReturn(dto);

        assertThat(userService.findByEmail("  Colleague@Bank.example ")).isSameAs(dto);
    }

    @Test
    @DisplayName("Admin: no account uses the email is a 404; a malformed email is a 400 without a query")
    void adminErrors() {
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("nobody@bank.example")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("nobody@bank.example"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(404));
        assertThatThrownBy(() -> userService.findByEmail("nobody"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).findFirstByEmailIgnoreCaseOrderByIdDesc("nobody");
    }

    @Test
    @DisplayName("Owner: the colleague is found, not yet a member; the organization is not locked")
    void ownerFindsColleague() {
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("colleague@bank.example")).thenReturn(Optional.of(colleague));

        LenderMemberLookupDTO found = organizationService.lookupMember(7L, 10L, "colleague@bank.example");

        assertThat(found).isEqualTo(new LenderMemberLookupDTO(9L, "colleague@bank.example", "Cleo", "Banks", false));
        verify(organizations, never()).lockById(10L);
    }

    @Test
    @DisplayName("Owner: a member or the owner is reported as member")
    void ownerSeesExistingMembership() {
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("colleague@bank.example")).thenReturn(Optional.of(colleague));
        when(members.existsByOrganizationIdAndUserId(10L, 9L)).thenReturn(true);
        assertThat(organizationService.lookupMember(7L, 10L, "colleague@bank.example").member()).isTrue();

        User owner = new User();
        owner.setId(7L);
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("owner@bank.example")).thenReturn(Optional.of(owner));
        assertThat(organizationService.lookupMember(7L, 10L, "owner@bank.example").member()).isTrue();
    }

    @Test
    @DisplayName("Owner: no account uses the email is a 404")
    void ownerUnknownEmail() {
        when(users.findFirstByEmailIgnoreCaseOrderByIdDesc("nobody@bank.example")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.lookupMember(7L, 10L, "nobody@bank.example"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(404));
    }

    @Test
    @DisplayName("A member who is not the owner gets 403, an outsider 404, and no account is looked up")
    void onlyTheOwnerLooksUp() {
        when(members.existsByOrganizationIdAndUserId(10L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> organizationService.lookupMember(8L, 10L, "colleague@bank.example"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(403));
        assertThatThrownBy(() -> organizationService.lookupMember(99L, 10L, "colleague@bank.example"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(404));
        verifyNoInteractions(users);
    }
}
