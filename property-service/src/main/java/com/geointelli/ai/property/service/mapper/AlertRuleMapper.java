package com.geointelli.ai.property.service.mapper;

import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.SavedSearch;

/** Copies the rule and computes the current pause setting without starting evaluation. */
@Component
public class AlertRuleMapper {
    public AlertRuleDTO toDTO(AlertRule rule) {
        SavedSearch search = rule.getSavedSearch();
        return new AlertRuleDTO(rule.getId(), rule.getName(), rule.getEventType(),
            rule.getProperty() == null ? null : rule.getProperty().getId(),
            search == null ? null : search.getId(), rule.getCadence(), rule.isPaused(),
            rule.isPaused() || (search != null && search.isPaused()), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
