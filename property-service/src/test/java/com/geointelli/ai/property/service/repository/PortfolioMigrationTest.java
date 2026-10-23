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

/** Checks relational constraints using an isolated H2 database in PostgreSQL mode, never the configured database. */
class PortfolioMigrationTest {
    private Connection connection;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:portfolio-" + UUID.randomUUID() + ";MODE=PostgreSQL");
        execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        execute("CREATE TABLE properties (id BIGINT PRIMARY KEY)");
        execute("INSERT INTO users VALUES (7)");
        execute("INSERT INTO properties VALUES (100)");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V16__add_portfolios_and_holdings.sql"));
        execute("INSERT INTO portfolios (id,user_id,name) VALUES (10,7,'Rentals'),(11,7,'Other')");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void propertyIsUniqueWithinPortfolioButAllowedAcrossPortfolios() throws Exception {
        execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,100)");
        assertThrows(SQLException.class, () ->
            execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,100)"));
        execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (11,100)");
        assertEquals(2, count("portfolio_holdings"));
    }

    @Test
    void deletingPortfolioCascadesHoldingsAndPreservesProperty() throws Exception {
        execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,100),(11,100)");
        execute("DELETE FROM portfolios WHERE id=10");
        assertEquals(1, count("portfolio_holdings"));
        assertEquals(1, count("properties"));
    }

    @Test
    void propertyAndOwnerForeignKeysAreEnforced() {
        assertThrows(SQLException.class, () ->
            execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,999)"));
        assertThrows(SQLException.class, () ->
            execute("INSERT INTO portfolios (user_id,name) VALUES (999,'Foreign')"));
    }

    @Test
    void cannotDeletePropertyWhileHeld() throws Exception {
        execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,100)");
        assertThrows(SQLException.class, () -> execute("DELETE FROM properties WHERE id=100"));
    }

    @Test
    void databaseRejectsInvalidShareAndNegativeAmounts() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO portfolio_holdings (portfolio_id,property_id,ownership_share_percent) VALUES (10,100,0)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO portfolio_holdings (portfolio_id,property_id,purchase_price) VALUES (10,100,-1)"));
    }

    @Test
    void databaseRejectsInconsistentLoanAndRentalFacts() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO portfolio_holdings (portfolio_id,property_id,outstanding_balance) VALUES (10,100,100)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO portfolio_holdings (portfolio_id,property_id,financing_type,loan_amount) VALUES (10,100,'CASH',100)"));
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO portfolio_holdings (portfolio_id,property_id,occupancy_status,monthly_rent) VALUES (10,100,'VACANT',100)"));
    }

    @Test
    void unknownFactsRemainNullAndShareDefaultsTo100() throws Exception {
        execute("INSERT INTO portfolio_holdings (portfolio_id,property_id) VALUES (10,100)");
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM portfolio_holdings")) {
            assertTrue(rows.next());
            assertNull(rows.getBigDecimal("purchase_price"));
            assertNull(rows.getBigDecimal("monthly_rent"));
            assertEquals(100, rows.getBigDecimal("ownership_share_percent").intValueExact());
            assertEquals("UNKNOWN", rows.getString("financing_type"));
            assertEquals("UNKNOWN", rows.getString("occupancy_status"));
        }
    }

    @Test
    void blankNamesAreRejected() {
        assertThrows(SQLException.class, () ->
            execute("INSERT INTO portfolios (user_id,name) VALUES (7,'   ')"));
    }
}
