package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import org.hibernate.Session;
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
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.impl.PropertySearchValidationServiceImpl;
import com.geointelli.ai.property.service.service.impl.SavedSearchServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

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

    /**
     * Hibernate compares the filters with the JSON it read back; any difference is written a second time at
     * commit, which moves {@code updated_at} past {@code created_at} and shows a false "updated" activity item.
     */
    @Test
    void searchCreatedThroughTheServiceHasNothingLeftToWrite() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.getCurrentUserEntity()).thenReturn(entityManager.getReference(User.class, 7L));
        SavedSearchFilters filters = new SavedSearchFilters();
        filters.setCountyId(31L);
        filters.setMinBedrooms(new BigDecimal("3"));
        filters.setMinBathrooms(new BigDecimal("1.5"));
        filters.setCity("  MIAMI ");
        SavedSearchRequest request = new SavedSearchRequest();
        request.setName("Miami homes");
        request.setFilters(filters);

        try (ValidatorFactory validation = Validation.buildDefaultValidatorFactory()) {
            new SavedSearchServiceImpl(searches, currentUser, mock(PropertySearchService.class),
                new PropertySearchValidationServiceImpl(validation.getValidator()), new ObjectMapper(),
                validation.getValidator(), mock(AlertEvaluationScheduleService.class)).create(request);
        }

        assertFalse(entityManager.unwrap(Session.class).isDirty(),
            "the saved search would be updated again right after it was created");
    }

    @Test
    void activityQueryOrdersByUpdateTimeThenNewestId() throws Exception {
        Long first = save(7L, "Changed last").getId();
        Long second = save(7L, "Tied older").getId();
        Long third = save(7L, "Tied newer").getId();
        jdbc.update("UPDATE saved_searches SET updated_at = TIMESTAMP '2026-11-02 10:00:00' WHERE id = ?", first);
        jdbc.update("UPDATE saved_searches SET updated_at = TIMESTAMP '2026-11-01 10:00:00' WHERE id IN (?, ?)",
            second, third);
        entityManager.clear();

        // The page's id sort comes after the ORDER BY of the method name; it only decides ties.
        List<SavedSearch> newest = searches.findByUser_IdOrderByUpdatedAtDesc(7L,
            PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));

        assertEquals(List.of(first, third), newest.stream().map(SavedSearch::getId).toList());
    }

    @Test
    void deletionDoesNotDeleteTheOwner() throws Exception {
        searches.delete(save(7L, "Delete"));
        searches.flush();
        assertEquals(0, searches.count());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }
}
