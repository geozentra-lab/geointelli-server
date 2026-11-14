package com.geointelli.ai.property.service.service;

/**
 * Invalidates incomplete scans when the user edits rule or saved-search
 * configuration.
 */
public interface AlertEvaluationScheduleService {
    void rescheduleRule(Long ruleId);

    void rescheduleSearch(Long savedSearchId);

    /**
     * Establishes a fresh valuation baseline after criteria or pause settings
     * change.
     */
    void resetValuationBaseline(Long ruleId);

    /** Resets valuation rules when a search's filters or pause state changes. */
    void resetValuationSearch(Long savedSearchId);
}
