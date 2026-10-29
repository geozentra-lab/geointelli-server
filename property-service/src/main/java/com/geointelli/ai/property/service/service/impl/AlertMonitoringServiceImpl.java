package com.geointelli.ai.property.service.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertMonitoringStatusDTO;
import com.geointelli.ai.property.service.entity.AlertEvaluationState;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.repository.AlertEvaluationStateRepository;
import com.geointelli.ai.property.service.repository.AlertOutboxRepository;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;
import com.geointelli.ai.property.service.service.AlertMonitoringService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Keeps manual evaluation asynchronous and enforces ownership before reading monitoring tables. */
@Service
@Transactional
public class AlertMonitoringServiceImpl implements AlertMonitoringService {
    private final AlertRuleRepository rules;
    private final AlertEvaluationStateRepository states;
    private final AlertOutboxRepository outbox;
    private final CurrentUserService currentUser;
    private final AlertEvaluationScheduleService schedule;
    private final AlertMonitoringProperties settings;
    public AlertMonitoringServiceImpl(AlertRuleRepository rules, AlertEvaluationStateRepository states,
            AlertOutboxRepository outbox, CurrentUserService currentUser,
            AlertEvaluationScheduleService schedule, AlertMonitoringProperties settings) {
        this.rules=rules; this.states=states; this.outbox=outbox; this.currentUser=currentUser;
        this.schedule=schedule; this.settings=settings;
    }
    @Override
    @Transactional(readOnly=true)
    public AlertMonitoringStatusDTO status(Long ruleId) {
        Long userId=currentUser.getCurrentUserEntity().getId();
        AlertRule rule=rules.findByIdAndUser_Id(ruleId,userId).orElseThrow(this::missing);
        boolean paused=paused(rule);
        if (!settings.isEnabled()) return new AlertMonitoringStatusDTO(ruleId,false,false,paused,
            "DISABLED",false,0,0,null,null,null,0,0,0);
        AlertEvaluationState state=states.findById(ruleId).orElse(new AlertEvaluationState());
        return new AlertMonitoringStatusDTO(ruleId,true,settings.getPublisher().isEnabled(),paused,
            state.getStatus(),state.getBaselineGeneration()!=null,state.getGeneration(),state.getMatchedCount(),
            state.getLastEvaluatedAt(),state.getNextEvaluationAt(),state.getLastErrorCode(),
            outbox.countByRuleIdAndStatus(ruleId,"PENDING"),outbox.countByRuleIdAndStatus(ruleId,"SENT"),
            outbox.countByRuleIdAndStatus(ruleId,"FAILED"));
    }
    @Override
    public void evaluate(Long ruleId) {
        Long userId=currentUser.getCurrentUserEntity().getId();
        AlertRule rule=rules.findOwnedForUpdate(ruleId,userId).orElseThrow(this::missing);
        if (!settings.isEnabled() || paused(rule) || rule.getEventType()!=AlertEventType.NEW_MATCH)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Monitoring must be enabled, unpaused and use a supported source");
        schedule.rescheduleRule(ruleId);
    }
    private boolean paused(AlertRule rule) {
        return rule.isPaused() || (rule.getSavedSearch()!=null && rule.getSavedSearch().isPaused());
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Alert rule not found"); }
}
