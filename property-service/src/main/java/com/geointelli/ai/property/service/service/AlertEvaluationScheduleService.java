package com.geointelli.ai.property.service.service;

/** Invalidates incomplete scans when the user edits rule or saved-search configuration. */
public interface AlertEvaluationScheduleService {
    void rescheduleRule(Long ruleId);
    void rescheduleSearch(Long savedSearchId);
}
