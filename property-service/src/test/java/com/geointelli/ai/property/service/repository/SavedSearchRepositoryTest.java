package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.entity.User;
import jakarta.persistence.EntityManager;

/** Executes owner-scoped queries and JSON persistence against an explicitly isolated H2 database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:saved-search-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SavedSearchRepositoryTest {
    @Autowired
    private SavedSearchRepository searches;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeAll
    void createSchema() throws Exception {
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__add_saved_searches.sql"));
        }
    }

    @BeforeEach
    void insertOwners() {
        jdbc.update("INSERT INTO users VALUES (7),(8)");
    }

    private SavedSearch save(Long userId, String name) throws Exception {
        SavedSearch saved = new SavedSearch();
        saved.setUser(entityManager.getReference(User.class, userId));
        saved.setName(name);
        saved.setFilters(new ObjectMapper().readTree("{\"countyId\":31,\"minBedrooms\":3,\"sortBy\":\"id\",\"direction\":\"asc\"}"));
        return searches.saveAndFlush(saved);
    }

    @Test
    void jsonFiltersAndMonitoringSettingsSurviveDatabaseRoundTrip() throws Exception {
        SavedSearch saved = save(7L, "Homes");
        Long id = saved.getId();
        entityManager.clear();
        SavedSearch found = searches.findByIdAndUser_Id(id, 7L).orElseThrow();
        assertTrue(found.getFilters().isObject());
        assertEquals(31, found.getFilters().get("countyId").asInt());
        assertEquals(3, found.getFilters().get("minBedrooms").asInt());
        assertEquals(SavedSearchCadence.DAILY, found.getCadence());
        assertFalse(found.isPaused());
        assertEquals(1, found.getFiltersVersion());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void ownerScopedReadsAndLocksHideForeignRows() throws Exception {
        Long id = save(7L, "Private").getId();
        assertTrue(searches.findByIdAndUser_Id(id, 8L).isEmpty());
        assertTrue(searches.findOwnedForUpdate(id, 8L).isEmpty());
        assertTrue(searches.findOwnedForUpdate(id, 7L).isPresent());
    }

    @Test
    void paginationCountsOnlyTheRequestedOwnersRows() throws Exception {
        save(7L, "First");
        Long newest = save(7L, "Second").getId();
        save(8L, "Other user");
        var page = searches.findByUser_Id(7L, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        assertEquals(2, page.getTotalElements());
        assertEquals(List.of(newest), page.getContent().stream().map(SavedSearch::getId).toList());
    }

    @Test
    void deletionDoesNotDeleteTheOwner() throws Exception {
        searches.delete(save(7L, "Delete"));
        searches.flush();
        assertEquals(0, searches.count());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }
}
