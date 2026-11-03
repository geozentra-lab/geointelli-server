-- Run against notification-service's database, never property-service's database.
-- Queue PENDING means queued; ASSIGNED refers to the batch whose status describes the provider outcome.
SELECT n.event_id,q.status AS queue_status,d.id AS delivery_id,d.status AS delivery_status,
       d.attempts,d.provider_message_id,d.last_error_code,d.created_at,d.accepted_at
FROM notification_email_queue q
JOIN notifications n ON n.id=q.notification_id
LEFT JOIN notification_email_deliveries d ON d.id=q.delivery_id
ORDER BY q.created_at DESC,q.notification_id DESC LIMIT 100;

-- ACCEPTED means SES accepted the request, not that the recipient received it.
-- UNKNOWN means acceptance cannot be proven either way. Never blindly reset UNKNOWN/SENDING to RETRY.
-- Inspect SES delivery/event evidence and retain original IDs before deciding on any manual replay.
-- A lost SES response may have no provider message ID; lack of one does not prove that no email was sent.
SELECT id,status,attempts,provider_message_id,last_error_code,lease_expires_at
FROM notification_email_deliveries WHERE status IN ('UNKNOWN','FAILED','RETRY','SENDING') ORDER BY id;

-- Existing visible inbox history must remain visible after opting out; future email-only rows are hidden.
SELECT id,event_id,in_app_visible,read_at FROM notifications ORDER BY id DESC LIMIT 20;
