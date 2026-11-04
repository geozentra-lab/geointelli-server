package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;

/** Executes the actual Criteria projection against an isolated database, including nullable child folios. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:recommendation-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PropertySearchServiceImpl.class)
class RecommendationCandidateQueryTest {
    @Autowired
    private PropertySearchServiceImpl search;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private PropertySearchValidationService validation;

    @BeforeEach
    void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS states(id BIGINT PRIMARY KEY, code VARCHAR(2), name VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS counties(id BIGINT PRIMARY KEY, name VARCHAR(100), state_id BIGINT)");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS properties(
                id BIGINT PRIMARY KEY, county_id BIGINT, folio VARCHAR(100), dor_code VARCHAR(100),
                dor_description VARCHAR(100), bedroom_count NUMERIC, bathroom_count NUMERIC,
                building_heated_area NUMERIC, lot_size DOUBLE PRECISION, year_built VARCHAR(100),
                neighborhood VARCHAR(100), neighborhood_description VARCHAR(100), parent_property_id BIGINT)
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS addresses(id BIGINT PRIMARY KEY, property_id BIGINT UNIQUE,
                address VARCHAR(100), city VARCHAR(100), zip VARCHAR(20))
            """);
        jdbc.update("INSERT INTO states VALUES (1, 'FL', 'Florida')");
        jdbc.update("INSERT INTO counties VALUES (31, 'Miami-Dade', 1), (32, 'Broward', 1), (33, 'Empty', 1)");
        jdbc.update("""
            INSERT INTO properties(id,county_id,folio,parent_property_id) VALUES
                (1,32,'same-folio',null),(2,31,'same-folio',null),(3,31,null,2),(4,null,null,null)
            """);
        jdbc.update("INSERT INTO addresses VALUES(1,2,'123 Test Street','Miami','33101')");
    }

    @Test
    void scopesCountiesAndPreservesDistinctParentChildRecordsAndMissingAddresses() {
        List<PropertySearchItem> result = search.findRecommendationCandidates(List.of(31L), 50);
        assertEquals(List.of(2L, 3L), result.stream().map(PropertySearchItem::id).toList());
        assertEquals("123 Test Street", result.get(0).address());
        assertEquals("FL", result.get(0).stateCode());
        assertNull(result.get(1).folio());
        assertNull(result.get(1).address());
    }

    @Test
    void combinesCountiesBeforeApplyingGlobalLimitAndStableOrder() {
        List<PropertySearchItem> result = search.findRecommendationCandidates(List.of(31L, 32L), 2);
        assertEquals(List.of(1L, 2L), result.stream().map(PropertySearchItem::id).toList());
    }

    @Test
    void noDataDoesNotBroadenGeography() {
        assertTrue(search.findRecommendationCandidates(List.of(33L), 20).isEmpty());
    }

    @Test
    void invalidCountyScopeCannotBecomeUnfilteredQuery() {
        assertThrows(IllegalArgumentException.class, () -> search.findRecommendationCandidates(List.of(), 20));
        assertThrows(IllegalArgumentException.class, () -> search.findRecommendationCandidates(List.of(-1L), 20));
        assertThrows(IllegalArgumentException.class, () -> search.findRecommendationCandidates(List.of(31L), 51));
    }
}
