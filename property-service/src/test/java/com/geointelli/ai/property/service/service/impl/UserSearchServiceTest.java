package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.dto.UserSuggestionDTO;
import com.geointelli.ai.property.service.mapper.UserMapper;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.repository.UserRepository.SuggestionRow;

/** Suggestions while an admin types an email or a name. */
class UserSearchServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(users, mock(UserMapper.class));

    private static SuggestionRow row(Long id, String email, String firstName, String lastName) {
        return new SuggestionRow() {
            @Override public Long getId() { return id; }
            @Override public String getEmail() { return email; }
            @Override public String getFirstName() { return firstName; }
            @Override public String getLastName() { return lastName; }
        };
    }

    @Test
    @DisplayName("The typed text becomes a range; each account gives its id, email and names")
    void suggestions() {
        when(users.searchByPrefix("dana j", "dana k", 5))
            .thenReturn(List.of(row(9L, "dana@bank.example", "Dana", "Jonas"), row(10L, null, "Dana", null)));

        assertThat(service.search(" Dana  J ", 5)).containsExactly(
            new UserSuggestionDTO(9L, "dana@bank.example", "Dana", "Jonas"),
            new UserSuggestionDTO(10L, null, "Dana", null));
    }

    @Test
    @DisplayName("Too short a text or a size outside 1 to 25: 400, the database is not queried")
    void invalid() {
        assertThatThrownBy(() -> service.search("d", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("dan", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("dan", 26)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users);
    }
}
