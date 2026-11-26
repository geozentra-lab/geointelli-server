package com.geointelli.ai.property.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Text typed in the admin user search, turned into the range of values that start with it. */
class UserSearchTest {
    private static final String MAX = new String(Character.toChars(Character.MAX_CODE_POINT));

    @Test
    @DisplayName("Trimmed, runs of spaces made one, lowercase; _ % and \\ are kept as typed")
    void normalises() {
        assertThat(UserSearch.prefix("  Dan ")).isEqualTo("dan");
        assertThat(UserSearch.prefix("JA")).isEqualTo("ja");
        assertThat(UserSearch.prefix("Dan  \t Smi")).isEqualTo("dan smi");
        assertThat(UserSearch.prefix("John_D%\\")).isEqualTo("john_d%\\");
    }

    @Test
    @DisplayName("The range ends at the prefix with its last character increased")
    void upperBound() {
        assertThat(UserSearch.upperBound("dan")).isEqualTo("dao");
        assertThat(UserSearch.upperBound("jane d")).isEqualTo("jane e");
        assertThat(UserSearch.upperBound("john_")).isEqualTo("john`");
        assertThat(UserSearch.upperBound("rené")).isEqualTo("renê");
        assertThat(UserSearch.upperBound("a퟿")).isEqualTo("a");
        assertThat(UserSearch.upperBound("a😀")).isEqualTo("a😁");
        assertThat(UserSearch.upperBound("ab" + MAX)).isEqualTo("ac");
        assertThat(UserSearch.upperBound("ab" + MAX + MAX)).isEqualTo("ac");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "d", "  d  "})
    @DisplayName("Fewer than 2 characters: 400")
    void tooShort(String q) {
        assertThatThrownBy(() -> UserSearch.prefix(q))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("q must have at least 2 characters");
    }

    @Test
    @DisplayName("At most 254 characters")
    void tooLong() {
        assertThat(UserSearch.prefix("A".repeat(254))).isEqualTo("a".repeat(254));
        assertThatThrownBy(() -> UserSearch.prefix("a".repeat(255)))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("q must be at most 254 characters");
    }

    @Test
    @DisplayName("Between 1 and 25 suggestions")
    void size() {
        assertThat(UserSearch.size(1)).isEqualTo(1);
        assertThat(UserSearch.size(25)).isEqualTo(25);
        for (int size : new int[] {0, -1, 26}) {
            assertThatThrownBy(() -> UserSearch.size(size))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("size must be between 1 and 25");
        }
    }
}
