package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.geointelli.ai.property.service.repository.JdbcValuationHistoryReader;

/** Opt-in, real PostgreSQL contract checks. Never points at the application's database. */
@EnabledIfEnvironmentVariable(named = "VALUATION_TEST_DB_URL", matches = ".+")
class ValuationRefreshDatabaseTest {
    private static final String SCHEMA = "valuation_test_" + UUID.randomUUID().toString().replace("-", "");
    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static String url;

    @BeforeAll
    static void prepare() throws Exception {
        url = System.getenv("VALUATION_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/geozentra_scrum59_java_test"))
            throw new IllegalStateException("Only the dedicated local geozentra_scrum59_java_test database is allowed");
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(System.getenv("DB_USER"));
        dataSource.setPassword(System.getenv("DB_PASSWORD"));
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        dataSource.setCurrentSchema(SCHEMA);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE property_value_predictions (property_id bigint)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V33__add_valuation_history_and_refresh_requests.sql")));
        jdbc.execute("CREATE TABLE verified_sources (id bigint PRIMARY KEY, value integer)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is strictly checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void clearQueue() {
        jdbc.execute("TRUNCATE property_valuation_refresh_requests, verified_sources");
    }

    @Test
    void rollbackRemovesSourceAndRefreshTogetherAndLaterChangesAreNotLost() {
        var service = new ValuationRefreshServiceImpl(jdbc, "PROPERTY");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO verified_sources VALUES (1, 1)");
            enqueue(service, "SALE_CHANGE", 301400L, 91L);
            throw new IllegalStateException("Simulated source failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM verified_sources", Integer.class)).isZero();
        assertThat(count()).isZero();
        transactions.executeWithoutResult(status -> {
            enqueue(service, "SALE_CHANGE", 301400L, 91L);
            enqueue(service, "SALE_CHANGE", 301400L, 91L);
        });
        assertThat(count()).isEqualTo(1);
        jdbc.update("UPDATE property_valuation_refresh_requests SET status='LEASED',lease_token=?,lease_expires_at=now()+interval '15 minutes'", UUID.randomUUID());
        transactions.executeWithoutResult(status -> enqueue(service,"SALE_CHANGE",301400L,91L));
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests WHERE status='PENDING'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT bool_and(source_changed_at=created_at AND created_at=next_attempt_at) FROM property_valuation_refresh_requests", Boolean.class)).isTrue();
    }

    @Test
    void bulkScopeCoalescesWithinTransactionAndRejectsIndependentAutoCommitEnqueue() throws Exception {
        var service = new ValuationRefreshServiceImpl(jdbc, "FULL_MARKET");
        transactions.executeWithoutResult(status -> {
            enqueue(service,"SALE_CHANGE",1L,91L);
            enqueue(service,"PROPERTY_CHANGE",2L,null);
        });
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT scope_type FROM property_valuation_refresh_requests",String.class)).isEqualTo("FULL_MARKET");
        try (var connection = dataSource.getConnection()) {
            assertThatThrownBy(() -> service.sourceChanged(connection,"PROPERTY_CHANGE",1L,null))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void retryIsNewAndIdempotentAndLeasedWorkCannotBeCancelled() {
        var service = new ValuationRefreshServiceImpl(jdbc,"PROPERTY");
        transactions.executeWithoutResult(status -> enqueue(service,"PROPERTY_CHANGE",301400L,null));
        UUID oldId = jdbc.queryForObject("SELECT request_id FROM property_valuation_refresh_requests",UUID.class);
        jdbc.update("UPDATE property_valuation_refresh_requests SET status='FAILED',attempts=5 WHERE request_id=?",oldId);
        var first = transactions.execute(status -> service.retry(oldId,"manual-1"));
        var second = transactions.execute(status -> service.retry(oldId,"manual-1"));
        assertThat(first.requestId()).isEqualTo(second.requestId()).isNotEqualTo(oldId);
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.attempts()).isZero();
        assertThat(service.get(oldId).status()).isEqualTo("FAILED");
        jdbc.update("UPDATE property_valuation_refresh_requests SET status='LEASED',lease_token=?,lease_expires_at=now()+interval '15 minutes' WHERE request_id=?",UUID.randomUUID(),first.requestId());
        assertThatThrownBy(() -> transactions.execute(status -> service.cancel(first.requestId())))
            .hasMessageContaining("409");
        assertThat(transactions.execute(status -> service.cancel(oldId)).status()).isEqualTo("CANCELLED");
    }

    @Test
    void historyUsesLoadOrderFrozenDecimalsAndNoInventedRunOrNaiveCreationTime() {
        String sql = """
            INSERT INTO property_value_prediction_history
            (property_id,provenance,predicted_price,forecast_1y,forecast_1y_low,forecast_3y_high,
             prediction_date,loaded_at,row_sha256,valuation_status,evidence)
            VALUES (301400,'SEEDED_CURRENT',671182.720123,710000.123456,600000.2,900000.99,
                    ?,?::timestamptz,repeat('a',64),'supported','{"last_sale":{"price":695000,"date":"2026-05-06"}}')
            """;
        jdbc.update(sql,Date.valueOf("2026-11-09"),"2026-11-08T01:00:00.123456Z");
        jdbc.update(sql,Date.valueOf("2026-11-08"),"2026-11-09T01:00:00.654321Z");
        var history = new JdbcValuationHistoryReader(jdbc).findByPropertyId(301400L,PageRequest.of(0,1));
        assertThat(history.getTotalElements()).isEqualTo(2);
        var snapshot = history.getContent().get(0);
        assertThat(snapshot.prediction().getPredictionDate()).hasToString("2026-11-08");
        assertThat(snapshot.prediction().getPredictedPrice()).isEqualByComparingTo(new BigDecimal("671182.720123"));
        assertThat(snapshot.prediction().getForecast1y()).isEqualByComparingTo("710000.123456");
        assertThat(snapshot.prediction().getForecast1yLow()).isEqualByComparingTo("600000.2");
        assertThat(snapshot.prediction().getForecast3yHigh()).isEqualByComparingTo("900000.99");
        assertThat(snapshot.prediction().getCreatedAt()).isNull();
        assertThat(snapshot.run()).isNull();
        assertThat(snapshot.loadedAt()).hasToString("2026-11-09T01:00:00.654321Z");
        assertThatThrownBy(() -> jdbc.update("UPDATE property_value_prediction_history SET predicted_price=1"))
            .hasMessageContaining("append-only");
    }

    private void enqueue(ValuationRefreshServiceImpl service,String trigger,Long propertyId,Long saleId) {
        transactions.executeWithoutResult(status -> {
            var connection = DataSourceUtils.getConnection(dataSource);
            try { service.sourceChanged(connection,trigger,propertyId,saleId); }
            catch (Exception error) { throw new IllegalStateException(error); }
            finally { DataSourceUtils.releaseConnection(connection,dataSource); }
        });
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests",Integer.class);
    }
}
