package com.geointelli.ai.property.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.util.UserSearch;

/**
 * "Starts with" on email, full name or last name, newest row of each email only (H2 in PostgreSQL mode, with the
 * search columns of V53 as H2 generated columns; the migration itself runs in UserSearchDatabaseTest).
 */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:user-search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserSearchRepositoryTest {
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("Native queries that read every column (lockForContact, findActiveRealtors) ignore the search columns")
    void entityQueriesIgnoreTheSearchColumns() {
        User star = (User) entityManager.createNativeQuery("SELECT * FROM users WHERE id = 41", User.class)
            .getSingleResult();
        User alias = (User) entityManager.createNativeQuery("SELECT u.* FROM users u WHERE u.id = 9", User.class)
            .getSingleResult();

        assertThat(star.getEmail()).isEqualTo("Jane@Example.com");
        assertThat(star.getFirstName()).isEqualTo("Jane");
        assertThat(alias.getLastName()).isEqualTo("Jonas");
    }

    @BeforeEach
    void accounts() {
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, keycloak_user_id VARCHAR(255) NOT NULL UNIQUE,"
            + " username VARCHAR(255), email VARCHAR(255), first_name VARCHAR(255), last_name VARCHAR(255),"
            + " persona VARCHAR(40), onboarding_completed_at TIMESTAMP WITH TIME ZONE,"
            + " created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,"
            + " email_search VARCHAR GENERATED ALWAYS AS (LOWER(email)),"
            + " full_name_search VARCHAR GENERATED ALWAYS AS"
            + " (LOWER(COALESCE(first_name, '') || ' ' || COALESCE(last_name, ''))),"
            + " last_name_search VARCHAR GENERATED ALWAYS AS (LOWER(last_name)))");
        account(5, "jane@example.com", "Old", "Name");          // older row of Jane's email
        account(41, "Jane@Example.com", "Jane", "Doe");
        account(7, "john_doe@example.com", "John", "Smith");
        account(8, "johnny@example.com", "Johnny", "Appleseed");
        account(9, "dana@bank.example", "Dana", "Jonas");
        account(10, null, "Danny", null);
        account(11, "x@example.com", null, "Daniels");
        account(12, "oldname@example.com", "Olga", "Ford");     // older row of the next one's email
        account(13, "OLDNAME@example.com", "Peter", "Pan");
    }

    private void account(long id, String email, String firstName, String lastName) {
        jdbc.update("INSERT INTO users (id, keycloak_user_id, username, email, first_name, last_name, persona,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'USER', LOCALTIMESTAMP, LOCALTIMESTAMP)",
            id, "kc-" + id, email, email, firstName, lastName);
    }

    private List<Long> search(String q, int size) {
        String prefix = UserSearch.prefix(q);
        return users.searchByPrefix(prefix, UserSearch.upperBound(prefix), size).stream()
            .map(UserRepository.SuggestionRow::getId).toList();
    }

    @Test
    @DisplayName("Each suggestion carries the id, email and names as stored")
    void suggestionFields() {
        UserRepository.SuggestionRow row = users.searchByPrefix("jane d", "jane e", 10).get(0);

        assertThat(row.getId()).isEqualTo(41L);
        assertThat(row.getEmail()).isEqualTo("Jane@Example.com");
        assertThat(row.getFirstName()).isEqualTo("Jane");
        assertThat(row.getLastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("Email, first name and last name prefixes, case ignored, by email with no email last")
    void prefixesByEmail() {
        assertThat(search("jo", 10)).containsExactly(9L, 7L, 8L);   // Jonas, john_doe@, johnny@
        assertThat(search("DAN", 10)).containsExactly(9L, 11L, 10L); // dana@, Daniels, Danny (no email)
        assertThat(search("JOHNNY@Ex", 10)).containsExactly(8L);
    }

    @Test
    @DisplayName("The full name matches from the first name on")
    void fullName() {
        assertThat(search("jane do", 10)).containsExactly(41L);
        assertThat(search("john  smi", 10)).containsExactly(7L);
        assertThat(search("doe", 10)).containsExactly(41L);
        assertThat(search("ane", 10)).isEmpty();
        assertThat(search("smith john", 10)).isEmpty();
    }

    @Test
    @DisplayName("Only the newest row of an email is listed, even when only an older row matches")
    void newestRowOfEachEmail() {
        assertThat(search("jane", 10)).containsExactly(41L);
        assertThat(search("old", 10)).containsExactly(13L);
        assertThat(search("olg", 10)).isEmpty();
    }

    @Test
    @DisplayName("_ and % are plain characters and the size limits the list")
    void literalCharactersAndSize() {
        assertThat(search("john_", 10)).containsExactly(7L);
        assertThat(search("jo%", 10)).isEmpty();
        assertThat(search("jo", 2)).containsExactly(9L, 7L);
    }
}
