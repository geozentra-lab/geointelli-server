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
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.AlertRuleMapper;
import jakarta.persistence.EntityManager;

/** Executes real owner-scoped reads, locking, pause mapping and cascade deletion in an isolated database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:alert-rule-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertRuleRepositoryTest {
    @Autowired
    private AlertRuleRepository rules;
    @Autowired
    private SavedSearchRepository searches;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    private SavedSearch search;

    @BeforeAll
    void createSchema() throws Exception {
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE properties (id BIGINT PRIMARY KEY)");
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__add_saved_searches.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V19__add_alert_rules.sql"));
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO users VALUES (7),(8)");
        jdbc.update("INSERT INTO properties VALUES (100)");
        search = new SavedSearch();
        search.setName("Homes");
        search.setUser(entityManager.getReference(User.class, 7L));
        search.setFilters(new ObjectMapper().createObjectNode().put("countyId", 31));
        search = searches.saveAndFlush(search);
    }

    private AlertRule saveSearchRule(Long owner) {
        AlertRule rule = new AlertRule();
        rule.setName("New homes");
        rule.setUser(entityManager.getReference(User.class, owner));
        rule.setSavedSearch(search);
        rule.setEventType(AlertEventType.NEW_MATCH);
        return rules.saveAndFlush(rule);
    }

    private AlertRule savePropertyRule(Long owner) {
        AlertRule rule = new AlertRule();
        rule.setName("Price");
        rule.setUser(entityManager.getReference(User.class, owner));
        rule.setProperty(entityManager.getReference(Property.class, 100L));
        rule.setEventType(AlertEventType.LISTING_PRICE_CHANGED);
        return rules.saveAndFlush(rule);
    }

    @Test
    void readAndLockQueriesHideForeignRules() {
        Long id = saveSearchRule(7L).getId();
        assertTrue(rules.findByIdAndUser_Id(id, 8L).isEmpty());
        assertTrue(rules.findOwnedForUpdate(id, 8L).isEmpty());
        assertTrue(rules.findOwnedForUpdate(id, 7L).isPresent());
    }

    @Test
    void lockingPropertyRuleWorksWithoutANullableSavedSearchJoin() {
        Long id = savePropertyRule(7L).getId();
        entityManager.clear();
        AlertRule found = rules.findOwnedForUpdate(id, 7L).orElseThrow();
        assertNull(found.getSavedSearch());
        assertEquals(100L, found.getProperty().getId());
    }

    @Test
    void listsOnlyOwnedRulesWithStablePaginationAndCounts() {
        saveSearchRule(7L);
        Long latest = savePropertyRule(7L).getId();
        savePropertyRule(8L);
        var page = rules.findByUser_Id(7L, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        assertEquals(2, page.getTotalElements());
        assertEquals(List.of(latest), page.getContent().stream().map(AlertRule::getId).toList());
    }

    @Test
    void duplicateLookupsAreScopedByOwnerTargetAndEvent() {
        saveSearchRule(7L);
        savePropertyRule(7L);
        assertTrue(rules.existsByUser_IdAndSavedSearch_IdAndEventType(7L, search.getId(), AlertEventType.NEW_MATCH));
        assertFalse(rules.existsByUser_IdAndSavedSearch_IdAndEventType(8L, search.getId(), AlertEventType.NEW_MATCH));
        assertTrue(rules.existsByUser_IdAndProperty_IdAndEventType(7L, 100L, AlertEventType.LISTING_PRICE_CHANGED));
        assertFalse(rules.existsByUser_IdAndProperty_IdAndEventType(7L, 100L, AlertEventType.VALUATION_CHANGED));
    }

    @Test
    void effectivePauseReadsTheCurrentSavedSearchState() {
        Long id = saveSearchRule(7L).getId();
        search.setPaused(true);
        searches.saveAndFlush(search);
        entityManager.clear();
        AlertRule found = rules.findByIdAndUser_Id(id, 7L).orElseThrow();
        var dto = new AlertRuleMapper().toDTO(found);
        assertFalse(dto.paused());
        assertTrue(dto.effectivePaused());
    }

    @Test
    void deletingSearchThroughJpaCascadesOnlyItsRules() {
        Long searchRuleId = saveSearchRule(7L).getId();
        Long propertyRuleId = savePropertyRule(7L).getId();
        searches.delete(search);
        searches.flush();
        entityManager.clear();
        assertTrue(rules.findByIdAndUser_Id(searchRuleId, 7L).isEmpty());
        assertTrue(rules.findByIdAndUser_Id(propertyRuleId, 7L).isPresent());
    }

    @Test
    void deletingRuleThroughJpaPreservesItsSearch() {
        AlertRule rule = saveSearchRule(7L);
        Long searchId = search.getId();
        rules.delete(rule);
        rules.flush();
        entityManager.clear();
        assertTrue(searches.findByIdAndUser_Id(searchId, 7L).isPresent());
    }
}
