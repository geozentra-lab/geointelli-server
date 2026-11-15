ALTER TABLE notification_preferences ADD COLUMN valuation_changed_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE notifications ADD CONSTRAINT ck_notification_valuation_target CHECK (
    event_type <> 'VALUATION_CHANGED' OR (alert_rule_id IS NOT NULL AND property_id IS NOT NULL));
