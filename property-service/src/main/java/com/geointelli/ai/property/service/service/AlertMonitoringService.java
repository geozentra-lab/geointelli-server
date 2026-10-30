package com.geointelli.ai.property.service.service;
import com.geointelli.ai.property.service.dto.AlertMonitoringStatusDTO;
/** Exposes monitoring progress only to the rule owner. */
public interface AlertMonitoringService {
    AlertMonitoringStatusDTO status(Long ruleId);
    void evaluate(Long ruleId);
}
