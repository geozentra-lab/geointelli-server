package com.geointelli.ai.property.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserEmailsTest {

    @Test
    @DisplayName("Surrounding spaces are removed; the case is kept, matching ignores it")
    void trims() {
        assertThat(UserEmails.forLookup("  Jane.Doe@Example.com ")).isEqualTo("Jane.Doe@Example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A missing email is a 400")
    void missing(String email) {
        assertThatThrownBy(() -> UserEmails.forLookup(email))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("email is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane", "@example.com", "jane@"})
    @DisplayName("Text that is not an email address is a 400")
    void malformed(String email) {
        assertThatThrownBy(() -> UserEmails.forLookup(email))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("email must be an email address");
    }

    @Test
    @DisplayName("More than 254 characters is a 400")
    void tooLong() {
        String email = "a".repeat(250) + "@b.co";
        assertThatThrownBy(() -> UserEmails.forLookup(email))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("email must be at most 254 characters");
        assertThat(UserEmails.forLookup("a".repeat(249) + "@b.co")).hasSize(254);
    }
}
