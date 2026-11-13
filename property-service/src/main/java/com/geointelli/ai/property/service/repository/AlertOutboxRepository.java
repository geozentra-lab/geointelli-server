package com.geointelli.ai.property.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.AlertOutboxEvent;

public interface AlertOutboxRepository extends JpaRepository<AlertOutboxEvent, Long> {
    long countByRuleIdAndStatus(Long ruleId, String status);

    /**
     * Guards one event per rule and published history version, independently of
     * scheduling retries.
     */
    boolean existsByRuleIdAndSourceChangeId(Long ruleId, String sourceChangeId);
}
