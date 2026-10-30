package com.geointelli.ai.property.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.AlertEvaluationState;

/** State is read/written inside the same transaction that locks the rule. */
public interface AlertEvaluationStateRepository extends JpaRepository<AlertEvaluationState, Long> {
}
