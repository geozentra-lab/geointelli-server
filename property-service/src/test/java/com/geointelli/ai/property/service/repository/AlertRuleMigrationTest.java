package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Tests rule constraints and target deletion against an isolated H2 PostgreSQL-mode database. */
class AlertRuleMigrationTest {
    private Connection connection;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:alert-" + UUID.randomUUID() + ";MODE=PostgreSQL");
        execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        execute("CREATE TABLE properties (id BIGINT PRIMARY KEY)");
        execute("INSERT INTO users VALUES (7),(8)");
        execute("INSERT INTO properties VALUES (100)");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__add_saved_searches.sql"));
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V19__add_alert_rules.sql"));
        execute("INSERT INTO saved_searches (id,user_id,name,filters) VALUES (10,7,'Homes','{}')");
    }

    @AfterEach
    void cleanup() throws Exception { if (connection != null) connection.close(); }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void searchRule() throws SQLException {
        execute("INSERT INTO alert_rules (user_id,name,event_type,saved_search_id) VALUES (7,'Homes','NEW_MATCH',10)");
    }

    private void propertyRule() throws SQLException {
        execute("INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (7,'Price','LISTING_PRICE_CHANGED',100)");
    }

    @Test
    void duplicateSearchAndPropertyTriggersAreRejected() throws Exception {
        searchRule();
        assertThrows(SQLException.class, this::searchRule);
        propertyRule();
        assertThrows(SQLException.class, this::propertyRule);
    }

    @Test
    void distinctEventsAndOwnersAreAllowed() throws Exception {
        propertyRule();
        execute("INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (7,'Value','VALUATION_CHANGED',100)");
        execute("INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (8,'Other owner','LISTING_PRICE_CHANGED',100)");
        assertEquals(3, count("alert_rules"));
    }

    @Test
    void exactlyOneTargetAndNewMatchSearchRequirementAreEnforced() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type) VALUES (7,'Invalid','NEW_MATCH')"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id,saved_search_id) VALUES (7,'Invalid','NEW_MATCH',100,10)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (7,'Invalid','NEW_MATCH',100)"));
    }

    @Test
    void missingTargetAndOwnerForeignKeysAreRejected() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,saved_search_id) VALUES (7,'Invalid','NEW_MATCH',999)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (999,'Invalid','VALUATION_CHANGED',100)"));
    }

    @Test
    void unsupportedEventsCadenceAndBlankNamesAreRejected() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (7,'Invalid','UNKNOWN',100)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id,cadence) VALUES (7,'Invalid','VALUATION_CHANGED',100,'HOURLY')"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO alert_rules (user_id,name,event_type,property_id) VALUES (7,'  ','VALUATION_CHANGED',100)"));
    }

    @Test
    void deletingSavedSearchCascadesOnlyItsRules() throws Exception {
        searchRule();
        propertyRule();
        execute("DELETE FROM saved_searches WHERE id=10");
        assertEquals(1, count("alert_rules"));
        assertEquals(1, count("properties"));
    }

    @Test
    void deletingPropertyCascadesOnlyItsRules() throws Exception {
        searchRule();
        propertyRule();
        execute("DELETE FROM properties WHERE id=100");
        assertEquals(1, count("alert_rules"));
        assertEquals(1, count("saved_searches"));
    }

    @Test
    void deletingRulePreservesBothTargets() throws Exception {
        searchRule();
        propertyRule();
        execute("DELETE FROM alert_rules");
        assertEquals(1, count("saved_searches"));
        assertEquals(1, count("properties"));
    }
}
