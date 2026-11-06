-- Contacts do not belong to a saved-search alert rule. Keep NEW_MATCH target requirements.
ALTER TABLE notifications ALTER COLUMN alert_rule_id DROP NOT NULL;
ALTER TABLE notifications ADD CONSTRAINT ck_notification_new_match_target CHECK (
    event_type <> 'NEW_MATCH' OR (alert_rule_id IS NOT NULL AND saved_search_id IS NOT NULL AND property_id IS NULL));
ALTER TABLE notifications ADD CONSTRAINT ck_notification_contact_target CHECK (
    event_type <> 'CONTACT_REQUEST' OR (alert_rule_id IS NULL AND saved_search_id IS NULL AND property_id IS NOT NULL));
ALTER TABLE notification_preferences ADD COLUMN contact_request_enabled BOOLEAN NOT NULL DEFAULT TRUE;
