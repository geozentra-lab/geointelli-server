package com.geointelli.ai.property.service.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL row claiming and bounded bulk operations participate in the surrounding JPA transaction. */
@Repository
public class AlertMonitoringRepository {
    private final JdbcTemplate jdbc;

    public AlertMonitoringRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void seedStates(Instant now) {
        jdbc.update("""
            INSERT INTO alert_evaluation_states(rule_id,next_evaluation_at)
            SELECT r.id, ? FROM alert_rules r
            WHERE NOT EXISTS (SELECT 1 FROM alert_evaluation_states e WHERE e.rule_id=r.id)
            ON CONFLICT (rule_id) DO NOTHING
            """, Timestamp.from(now));
    }

    /** The database lock lasts for one short batch transaction, not for the entire scan. */
    public Long lockNextDueRule(Instant now) {
        List<Long> ids = jdbc.queryForList("""
            SELECT r.id FROM alert_rules r
            JOIN alert_evaluation_states e ON e.rule_id=r.id
            LEFT JOIN saved_searches s ON s.id=r.saved_search_id
            WHERE e.next_evaluation_at<=? AND NOT r.paused
              AND (r.saved_search_id IS NULL OR NOT s.paused)
            ORDER BY e.next_evaluation_at,r.id LIMIT 1 FOR UPDATE OF r SKIP LOCKED
            """, Long.class, Timestamp.from(now));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void addMatches(long ruleId, long generation, List<Long> ids) {
        jdbc.batchUpdate("INSERT INTO alert_match_memberships(rule_id,generation,property_id) VALUES (?,?,?)",
            ids, 1000, (statement, id) -> {
                statement.setLong(1, ruleId); statement.setLong(2, generation); statement.setLong(3, id);
            });
    }

    public long countNew(long ruleId, long generation, long baseline) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM alert_match_memberships n
            WHERE n.rule_id=? AND n.generation=?
              AND NOT EXISTS(SELECT 1 FROM alert_match_memberships b
                WHERE b.rule_id=n.rule_id AND b.generation=? AND b.property_id=n.property_id)
            """, Long.class, ruleId, generation, baseline);
    }

    public List<Long> previewNew(long ruleId, long generation, long baseline) {
        return jdbc.queryForList("""
            SELECT n.property_id FROM alert_match_memberships n
            WHERE n.rule_id=? AND n.generation=?
              AND NOT EXISTS(SELECT 1 FROM alert_match_memberships b
                WHERE b.rule_id=n.rule_id AND b.generation=? AND b.property_id=n.property_id)
            ORDER BY n.property_id LIMIT 100
            """, Long.class, ruleId, generation, baseline);
    }

    public void retainOnly(long ruleId, long generation) {
        jdbc.update("DELETE FROM alert_match_memberships WHERE rule_id=? AND generation<>?", ruleId, generation);
    }

    public void clearStaging(long ruleId, Long baseline) {
        if (baseline == null) jdbc.update("DELETE FROM alert_match_memberships WHERE rule_id=?", ruleId);
        else jdbc.update("DELETE FROM alert_match_memberships WHERE rule_id=? AND generation<>?", ruleId, baseline);
    }

    public String recipient(long userId) {
        return jdbc.queryForObject("SELECT keycloak_user_id FROM users WHERE id=?", String.class, userId);
    }

    public void rescheduleRule(long ruleId, Instant now) {
        jdbc.update("""
            INSERT INTO alert_evaluation_states(rule_id,next_evaluation_at)
            SELECT id, ? FROM alert_rules WHERE id=?
            ON CONFLICT (rule_id) DO UPDATE SET next_evaluation_at=EXCLUDED.next_evaluation_at,
                running=FALSE,status='READY'
            """, Timestamp.from(now), ruleId);
    }

    public void rescheduleSearch(long searchId, Instant now) {
        jdbc.update("""
            UPDATE alert_evaluation_states SET next_evaluation_at=?,running=FALSE,status='READY'
            WHERE rule_id IN (SELECT id FROM alert_rules WHERE saved_search_id=?)
            """, Timestamp.from(now), searchId);
    }

    public Long lockNextOutbox(Instant now) {
        List<Long> ids = jdbc.queryForList("""
            SELECT id FROM alert_outbox WHERE status='PENDING' AND next_attempt_at<=?
            ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, Long.class, Timestamp.from(now));
        return ids.isEmpty() ? null : ids.get(0);
    }
}
