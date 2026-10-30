package com.geointelli.ai.property.service.service;

/** Resumable scheduled evaluation; each batch is an independent transaction. */
public interface AlertEvaluationService {
    void prepare();
    boolean processNextBatch();
    void recordFailure(Long ruleId);
}
