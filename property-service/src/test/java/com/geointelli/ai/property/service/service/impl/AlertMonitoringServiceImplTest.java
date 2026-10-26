package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.repository.*;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;
import com.geointelli.ai.property.service.service.CurrentUserService;
class AlertMonitoringServiceImplTest {
    final AlertRuleRepository rules=mock(AlertRuleRepository.class);
    final AlertEvaluationStateRepository states=mock(AlertEvaluationStateRepository.class);
    final AlertOutboxRepository outbox=mock(AlertOutboxRepository.class);
    final CurrentUserService current=mock(CurrentUserService.class);
    final AlertEvaluationScheduleService schedule=mock(AlertEvaluationScheduleService.class);
    final AlertMonitoringProperties settings=new AlertMonitoringProperties();
    final AlertMonitoringServiceImpl service=new AlertMonitoringServiceImpl(rules,states,outbox,current,schedule,settings);
    final AlertRule rule=new AlertRule();
    @BeforeEach void setup() {
        User user=new User(); user.setId(8L); when(current.getCurrentUserEntity()).thenReturn(user);
        rule.setId(1L); rule.setEventType(AlertEventType.NEW_MATCH);
        when(rules.findByIdAndUser_Id(1L,8L)).thenReturn(Optional.of(rule));
        when(rules.findOwnedForUpdate(1L,8L)).thenReturn(Optional.of(rule));
    }
    @Test void disabledStatusDoesNotQueryNewTables() {
        assertEquals("DISABLED",service.status(1L).status()); verifyNoInteractions(states,outbox);
    }
    @Test void missingOwnedRuleIs404() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.status(2L)).getStatusCode().value());
    }
    @Test void manualScanIsQueuedWhenEnabled() {
        settings.setEnabled(true); service.evaluate(1L); verify(schedule).rescheduleRule(1L);
    }
    @Test void pausedSavedSearchPreventsManualScan() {
        settings.setEnabled(true); SavedSearch saved=new SavedSearch(); saved.setPaused(true); rule.setSavedSearch(saved);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.evaluate(1L)).getStatusCode().value());
        verifyNoInteractions(schedule);
    }
    @Test void disabledEvaluationIs409() {
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.evaluate(1L)).getStatusCode().value());
    }
}
