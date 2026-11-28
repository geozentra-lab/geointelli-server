package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.LenderMemberLookupDTO;
import com.geointelli.ai.property.service.entity.LenderOrganization;
import com.geointelli.ai.property.service.entity.LenderOrganizationMember;
import com.geointelli.ai.property.service.mapper.UserMapper;
import com.geointelli.ai.property.service.repository.LenderOrganizationMemberRepository;
import com.geointelli.ai.property.service.repository.LenderOrganizationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.repository.UserRepository.SuggestionRow;

/** Suggestions while the owner (7) of a lender organization (10) types a colleague's email or name. */
class LenderMemberSearchServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final LenderOrganizationRepository organizations = mock(LenderOrganizationRepository.class);
    private final LenderOrganizationMemberRepository members = mock(LenderOrganizationMemberRepository.class);
    private final LenderOrganizationServiceImpl service =
        new LenderOrganizationServiceImpl(organizations, members, users);

    @BeforeEach
    void organization() {
        LenderOrganization organization = new LenderOrganization();
        organization.setId(10L);
        organization.setOwnerUserId(7L);
        when(organizations.findById(10L)).thenReturn(Optional.of(organization));
    }

    private static SuggestionRow row(Long id, String email, String firstName, String lastName) {
        return new SuggestionRow() {
            @Override public Long getId() { return id; }
            @Override public String getEmail() { return email; }
            @Override public String getFirstName() { return firstName; }
            @Override public String getLastName() { return lastName; }
        };
    }

    private static LenderOrganizationMember membership(Long userId) {
        LenderOrganizationMember member = new LenderOrganizationMember();
        member.setOrganizationId(10L);
        member.setUserId(userId);
        return member;
    }

    private static int status(Throwable error) {
        return ((ResponseStatusException) error).getStatusCode().value();
    }

    @Test
    @DisplayName("The admin search's accounts in its order; the owner and current members are marked, in one query")
    void suggestionsWithMembership() {
        when(users.searchByPrefix("dana j", "dana k", 5)).thenReturn(List.of(
            row(8L, "dana.j@bank.example", "Dana", "Jay"),
            row(7L, "dana.owner@bank.example", "Dana", "Jordan"),
            row(9L, "dana@bank.example", "Dana", "Jonas"),
            row(12L, null, "Dana", "Jones")));
        when(members.findByOrganizationIdAndUserIdIn(10L, List.of(8L, 7L, 9L, 12L)))
            .thenReturn(List.of(membership(8L)));

        assertThat(service.searchMembers(7L, 10L, " Dana  J ", 5)).containsExactly(
            new LenderMemberLookupDTO(8L, "dana.j@bank.example", "Dana", "Jay", true),
            new LenderMemberLookupDTO(7L, "dana.owner@bank.example", "Dana", "Jordan", true),
            new LenderMemberLookupDTO(9L, "dana@bank.example", "Dana", "Jonas", false),
            new LenderMemberLookupDTO(12L, null, "Dana", "Jones", false));
        verify(organizations, never()).lockById(10L);
    }

    @Test
    @DisplayName("Same accounts and order as the admin search for the same text and size")
    void sameListAsTheAdminSearch() {
        when(users.searchByPrefix("da", "db", 10)).thenReturn(List.of(
            row(8L, "dan@bank.example", "Dan", "Ray"), row(9L, "dana@bank.example", "Dana", "Jonas")));
        UserServiceImpl admin = new UserServiceImpl(users, mock(UserMapper.class));

        assertThat(service.searchMembers(7L, 10L, "DA", 10))
            .extracting(LenderMemberLookupDTO::userId, LenderMemberLookupDTO::email, LenderMemberLookupDTO::firstName,
                LenderMemberLookupDTO::lastName)
            .containsExactlyElementsOf(admin.search("DA", 10).stream()
                .map(account -> tuple(account.id(), account.email(), account.firstName(), account.lastName()))
                .toList());
    }

    @Test
    @DisplayName("Nothing matches: an empty list, and membership is not queried")
    void nothingMatches() {
        when(users.searchByPrefix("zz", "z{", 10)).thenReturn(List.of());

        assertThat(service.searchMembers(7L, 10L, "zz", 10)).isEmpty();
        verifyNoInteractions(members);
    }

    @Test
    @DisplayName("Too short or missing text, or a size outside 1 to 25: 400, no account is searched")
    void invalid() {
        assertThatThrownBy(() -> service.searchMembers(7L, 10L, "d", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchMembers(7L, 10L, "  ", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchMembers(7L, 10L, "dan", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchMembers(7L, 10L, "dan", 26))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("A member who is not the owner gets 403, an outsider or unknown organization 404; nobody is searched")
    void onlyTheOwnerSearches() {
        when(members.existsByOrganizationIdAndUserId(10L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> service.searchMembers(8L, 10L, "dan", 10))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(403));
        assertThatThrownBy(() -> service.searchMembers(99L, 10L, "dan", 10))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(404));
        assertThatThrownBy(() -> service.searchMembers(7L, 11L, "dan", 10))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(status(error)).isEqualTo(404));
        verifyNoInteractions(users);
    }
}
