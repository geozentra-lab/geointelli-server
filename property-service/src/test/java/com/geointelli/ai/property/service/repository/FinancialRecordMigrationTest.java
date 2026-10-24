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

/** Tests V16/V17 relationships and deletion using isolated H2 PostgreSQL mode, never RDS. */
class FinancialRecordMigrationTest {
    private Connection connection;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:financial-" + UUID.randomUUID() + ";MODE=PostgreSQL");
        execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        execute("CREATE TABLE properties (id BIGINT PRIMARY KEY)");
        execute("INSERT INTO users VALUES (7)");
        execute("INSERT INTO properties VALUES (100)");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V16__add_portfolios_and_holdings.sql"));
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V17__add_holding_financial_records.sql"));
        execute("INSERT INTO portfolios (id,user_id,name) VALUES (10,7,'Rentals')");
        execute("INSERT INTO portfolio_holdings (id,portfolio_id,property_id) VALUES (20,10,100)");
        insert("RENT", "2500", "2025-01-10");
    }

    @AfterEach
    void cleanup() throws Exception { if (connection != null) connection.close(); }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private void insert(String category, String amount, String date) throws SQLException {
        execute("INSERT INTO holding_financial_records (holding_id,category,amount,transaction_date) VALUES (20,'"
            + category + "'," + amount + ",'" + date + "')");
    }

    private long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void recordDeletionPreservesParentsAndProperty() throws Exception {
        execute("DELETE FROM holding_financial_records");
        assertEquals(1, count("portfolio_holdings"));
        assertEquals(1, count("portfolios"));
        assertEquals(1, count("properties"));
    }

    @Test
    void holdingDeletionCascadesRecordsAndPreservesProperty() throws Exception {
        execute("DELETE FROM portfolio_holdings WHERE id=20");
        assertEquals(0, count("holding_financial_records"));
        assertEquals(1, count("properties"));
    }

    @Test
    void portfolioDeletionCascadesHoldingAndActualRecords() throws Exception {
        execute("DELETE FROM portfolios WHERE id=10");
        assertEquals(0, count("holding_financial_records"));
        assertEquals(0, count("portfolio_holdings"));
        assertEquals(1, count("properties"));
    }

    @Test
    void rejectsInvalidAmountsCurrenciesAndCategories() {
        assertThrows(SQLException.class, () -> insert("RENT", "0", "2025-01-10"));
        assertThrows(SQLException.class, () -> insert("REPAIRS", "-1", "2025-01-10"));
        assertThrows(SQLException.class, () -> insert("UNKNOWN", "10", "2025-01-10"));
        assertThrows(SQLException.class, () -> execute("UPDATE holding_financial_records SET currency='EUR'"));
    }

    @Test
    void periodMustBeCompleteAndOrdered() throws Exception {
        assertThrows(SQLException.class, () -> execute("UPDATE holding_financial_records SET period_start='2025-01-01'"));
        assertThrows(SQLException.class, () -> execute(
            "UPDATE holding_financial_records SET period_start='2025-02-01',period_end='2025-01-01'"));
        execute("UPDATE holding_financial_records SET period_start='2025-01-01',period_end='2025-01-31'");
    }

    @Test
    void unknownCoveredPeriodRemainsNull() throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM holding_financial_records")) {
            assertTrue(rows.next());
            assertNull(rows.getDate("period_start"));
            assertNull(rows.getDate("period_end"));
            assertEquals("USD", rows.getString("currency"));
        }
    }

    @Test
    void repeatedAmountsAndDatesCanRepresentDistinctActualPayments() throws Exception {
        insert("RENT", "2500", "2025-01-10");
        assertEquals(2, count("holding_financial_records"));
    }

    @Test
    void cannotAttachRecordToMissingHolding() {
        assertThrows(SQLException.class, () -> execute(
            "INSERT INTO holding_financial_records (holding_id,category,amount,transaction_date) VALUES (999,'RENT',10,'2025-01-10')"));
    }
}
