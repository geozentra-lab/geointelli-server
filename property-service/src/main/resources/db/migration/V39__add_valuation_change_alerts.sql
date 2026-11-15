ALTER TABLE alert_rules
    ADD COLUMN minimum_change_percent NUMERIC(12,4) NOT NULL DEFAULT 0,
    ADD COLUMN change_direction VARCHAR(12) NOT NULL DEFAULT 'ANY',
    ADD CONSTRAINT ck_alert_minimum_change CHECK (minimum_change_percent BETWEEN 0 AND 100000),
    ADD CONSTRAINT ck_alert_change_direction CHECK (change_direction IN ('ANY','INCREASE','DECREASE'));

-- One durable observation per rule/property. History IDs are publication identities, never prediction IDs.
-- No FK to history: append-only publication data is owned by Python.
CREATE TABLE alert_valuation_observations (
    rule_id BIGINT NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    property_id BIGINT NOT NULL,
    cursor_history_id BIGINT NOT NULL DEFAULT 0,
    comparison_history_id BIGINT,
    seen_generation BIGINT NOT NULL,
    PRIMARY KEY (rule_id, property_id)
);

ALTER TABLE alert_outbox ADD COLUMN source_change_id VARCHAR(200);
ALTER TABLE alert_outbox DROP CONSTRAINT uq_alert_outbox_generation;
-- NEW_MATCH still emits one summary per scan; valuation rules may emit multiple changes per scan.
CREATE UNIQUE INDEX uq_alert_outbox_new_match_generation ON alert_outbox(rule_id,generation)
    WHERE event_type='NEW_MATCH';
ALTER TABLE alert_outbox ADD CONSTRAINT uq_alert_outbox_source_change UNIQUE(rule_id,source_change_id);
