package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Runs against an explicitly supplied, disposable PostgreSQL database, never the application datasource. */
@EnabledIfSystemProperty(named="alert.test.postgres.url", matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
class AlertMonitoringRepositoryTest {
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    final Instant now=Instant.parse("2026-10-25T10:00:00Z");
    @BeforeEach void setup() {
        source=new DriverManagerDataSource(System.getProperty("alert.test.postgres.url"),"alert_test","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA IF EXISTS alert_monitor_test CASCADE");
        jdbc.execute("CREATE SCHEMA alert_monitor_test");
        Properties properties=new Properties();
        properties.setProperty("currentSchema","alert_monitor_test");
        source.setConnectionProperties(properties);
        jdbc.execute("CREATE TABLE saved_searches(id BIGINT PRIMARY KEY, paused BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE alert_rules(id BIGINT PRIMARY KEY, saved_search_id BIGINT REFERENCES saved_searches(id), paused BOOLEAN DEFAULT FALSE)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V20__add_alert_monitoring_and_outbox.sql")).execute(source);
        jdbc.update("INSERT INTO saved_searches(id) VALUES(1)");
        jdbc.update("INSERT INTO alert_rules(id,saved_search_id) VALUES(1,1),(2,1)");
    }
    @AfterEach void cleanup() { jdbc.execute("DROP SCHEMA alert_monitor_test CASCADE"); }
    @Test void twoSessionsClaimDifferentRulesAndRollbackReleasesClaim() throws Exception {
        AlertMonitoringRepository repository=new AlertMonitoringRepository(jdbc); repository.seedStates(now); repository.seedStates(now);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM alert_evaluation_states",Integer.class));
        try (Connection a=source.getConnection(); Connection b=source.getConnection()) {
            a.setAutoCommit(false); b.setAutoCommit(false);
            AlertMonitoringRepository first=new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(a,true)));
            AlertMonitoringRepository second=new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(b,true)));
            assertEquals(1L,first.lockNextDueRule(now)); assertEquals(2L,second.lockNextDueRule(now));
            a.rollback(); assertEquals(1L,second.lockNextDueRule(now)); b.rollback();
        }
    }
    @Test void snapshotDifferenceAndRollbackPreserveBaseline() throws Exception {
        AlertMonitoringRepository repository=new AlertMonitoringRepository(jdbc);
        repository.addMatches(1,1,List.of(10L,20L)); repository.addMatches(1,2,List.of(20L,30L));
        assertEquals(1,repository.countNew(1,2,1)); assertEquals(List.of(30L),repository.previewNew(1,2,1));
        try(Connection a=source.getConnection()) {
            a.setAutoCommit(false);
            AlertMonitoringRepository tx=new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(a,true)));
            tx.retainOnly(1,2); a.rollback();
        }
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM alert_match_memberships",Integer.class));
        jdbc.update("DELETE FROM alert_rules WHERE id=1");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM alert_match_memberships",Integer.class));
    }
    @Test void outboxClaimsSkipOtherSessionsAndGenerationIsUnique() throws Exception {
        String insert="INSERT INTO alert_outbox(rule_id,generation,recipient_keycloak_id,event_type,occurred_at,payload,next_attempt_at) VALUES(1,?, 'user', 'NEW_MATCH',CURRENT_TIMESTAMP,'{}',TIMESTAMPTZ '2020-01-01')";
        jdbc.update(insert,1); jdbc.update(insert,2);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(insert,1));
        try(Connection a=source.getConnection(); Connection b=source.getConnection()) {
            a.setAutoCommit(false); b.setAutoCommit(false);
            AlertMonitoringRepository first=new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(a,true)));
            AlertMonitoringRepository second=new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(b,true)));
            assertNotEquals(first.lockNextOutbox(now),second.lockNextOutbox(now)); a.rollback(); b.rollback();
        }
    }
}
