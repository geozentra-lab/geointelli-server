package com.geointelli.ai.property.service.exception;

/** Carries the selected rule through transaction rollback so a separate transaction can schedule its retry. */
public class AlertEvaluationException extends RuntimeException {
    private final Long ruleId;
    public AlertEvaluationException(Long ruleId, RuntimeException cause) {
        super("Alert evaluation batch failed", cause); this.ruleId = ruleId;
    }
    public Long getRuleId() { return ruleId; }
}
