package com.geointelli.ai.property.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.entity.User;

/** The newest account wins when old rows share an email; the case is ignored (H2 in PostgreSQL mode). */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:user-email-lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserEmailLookupRepositoryTest {
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void accounts() {
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, keycloak_user_id VARCHAR(255) NOT NULL UNIQUE,"
            + " username VARCHAR(255), email VARCHAR(255), first_name VARCHAR(255), last_name VARCHAR(255),"
            + " persona VARCHAR(40), onboarding_completed_at TIMESTAMP WITH TIME ZONE,"
            + " created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        account(5, "kc-old", "jane@example.com", "Old");
        account(41, "kc-new", "Jane@Example.com", "New");
        account(7, "kc-other", "john@example.com", "John");
    }

    private void account(long id, String keycloakId, String email, String firstName) {
        jdbc.update("INSERT INTO users (id, keycloak_user_id, username, email, first_name, persona, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, 'USER', LOCALTIMESTAMP, LOCALTIMESTAMP)", id, keycloakId, email, email, firstName);
    }

    @Test
    @DisplayName("Two rows share the email: the newest one is returned, whatever the case")
    void newestAccountIgnoringCase() {
        User found = users.findFirstByEmailIgnoreCaseOrderByIdDesc("JANE@example.COM").orElseThrow();

        assertThat(found.getId()).isEqualTo(41L);
        assertThat(found.getFirstName()).isEqualTo("New");
        assertThat(users.findFirstByEmailIgnoreCaseOrderByIdDesc("john@example.com")).map(User::getId).contains(7L);
    }

    @Test
    @DisplayName("Only the exact email matches")
    void exactEmailOnly() {
        assertThat(users.findFirstByEmailIgnoreCaseOrderByIdDesc("jane@example")).isEmpty();
        assertThat(users.findFirstByEmailIgnoreCaseOrderByIdDesc("ane@example.com")).isEmpty();
        assertThat(users.findFirstByEmailIgnoreCaseOrderByIdDesc("nobody@example.com")).isEmpty();
    }
}
