package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.dto.AlertRulePageDTO;
import com.geointelli.ai.property.service.dto.AlertRuleRequest;

/** Configures owned rules; evaluation, deduplication of detected changes and queue delivery belong to SCRUM-68. */
public interface AlertRuleService {
    /** Creates one rule for an existing property or an owned saved search; duplicate target/event rules return 409. */
    AlertRuleDTO create(AlertRuleRequest request);
    /** Lists the user's rules, newest ID first. */
    AlertRulePageDTO list(int page, int size);
    /** Returns one owned rule; missing and foreign IDs both return 404. */
    AlertRuleDTO get(Long id);
    /** Changes supplied name/cadence/paused fields; the target and trigger remain stable. */
    AlertRuleDTO update(Long id, AlertRuleRequest request);
    /** Permanently deletes the rule only, preserving the target property or saved search. */
    void delete(Long id);
}
