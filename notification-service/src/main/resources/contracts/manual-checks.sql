-- Run in geozentra_notifications, never the property-service database.
SELECT id,event_id,recipient_keycloak_id,event_type,created_at,read_at
FROM notifications ORDER BY id DESC LIMIT 20;
SELECT event_id,source,received_at FROM consumed_alert_events ORDER BY received_at DESC LIMIT 20;
-- Repeated delivery must not produce duplicate notifications.
SELECT event_id,COUNT(*) FROM notifications GROUP BY event_id HAVING COUNT(*)>1;
-- Unsupported or malformed events belong in RabbitMQ's DLQ, not these tables.
