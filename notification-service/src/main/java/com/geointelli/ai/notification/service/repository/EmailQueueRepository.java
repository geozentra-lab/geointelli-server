package com.geointelli.ai.notification.service.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Small SQL operations for uniquely queuing and claiming notifications inside
 * the caller's transaction.
 */
@Repository
public class EmailQueueRepository {
    private final JdbcTemplate jdbc;

    public EmailQueueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(long notificationId, String recipient, Instant now) {
        jdbc.update(
                "INSERT INTO notification_email_queue(notification_id,recipient_keycloak_id,created_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",
                notificationId, recipient, Timestamp.from(now));
    }

    public List<Item> pending(String recipient, int limit) {
        return jdbc.query(
                "SELECT n.id,n.message,CASE WHEN n.event_type='CONTACT_REQUEST' THEN n.title ELSE n.payload->>'ruleName' END AS rule_name FROM notification_email_queue q JOIN notifications n ON n.id=q.notification_id "
                        + "JOIN notification_preferences p ON p.recipient_keycloak_id=q.recipient_keycloak_id "
                        + "WHERE q.recipient_keycloak_id=? AND q.status='PENDING' "
                        + "AND ((n.event_type='NEW_MATCH' AND p.new_match_enabled) OR (n.event_type='CONTACT_REQUEST' AND p.contact_request_enabled)) ORDER BY q.created_at,q.notification_id LIMIT ? FOR UPDATE OF q SKIP LOCKED",
                (row, index) -> new Item(row.getLong("id"), row.getString("rule_name"), row.getString("message")),
                recipient, limit);
    }

    public void assign(List<Item> items, long deliveryId) {
        for (Item item : items)
            jdbc.update(
                    "UPDATE notification_email_queue SET status='ASSIGNED',delivery_id=? WHERE notification_id=? AND status='PENDING'",
                    deliveryId, item.id());
    }

    public void suppressPending(String recipient) {
        jdbc.update(
                "UPDATE notification_email_queue SET status='SUPPRESSED' WHERE recipient_keycloak_id=? AND status='PENDING'",
                recipient);
    }

    public void suppressPendingType(String recipient, String eventType) {
        jdbc.update("UPDATE notification_email_queue q SET status='SUPPRESSED' FROM notifications n "
            + "WHERE n.id=q.notification_id AND q.recipient_keycloak_id=? AND q.status='PENDING' AND n.event_type=?",
            recipient, eventType);
    }

    public record Item(long id, String ruleName, String message) {
    }
}
