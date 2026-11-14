package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.AlertEvaluationState;
import com.geointelli.ai.property.service.entity.AlertRule;

/**
 * Evaluates valuation changes using immutable model publications, never sale or
 * listing-price substitutes.
 */
public interface ValuationAlertEvaluationService {
    /**
     * Processes one bounded batch inside the transaction that holds the parent
     * alert-rule lock.
     */
    void evaluate(AlertRule rule, AlertEvaluationState state);
}
