package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.repository.AlertMonitoringRepository;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;

/** Participates in the caller's transaction; feature-off installations do not access the new tables. */
@Service
public class AlertEvaluationScheduleServiceImpl implements AlertEvaluationScheduleService {
    private final AlertMonitoringRepository repository;
    private final AlertMonitoringProperties settings;
    private final Clock clock;

    public AlertEvaluationScheduleServiceImpl(AlertMonitoringRepository repository, AlertMonitoringProperties settings,
            @Qualifier("alertClock") Clock clock) {
        this.repository = repository; this.settings = settings; this.clock = clock;
    }

    @Override
    public void rescheduleRule(Long ruleId) {
        if (settings.isEnabled()) repository.rescheduleRule(ruleId, clock.instant());
    }

    @Override
    public void rescheduleSearch(Long savedSearchId) {
        if (settings.isEnabled()) repository.rescheduleSearch(savedSearchId, clock.instant());
    }
}
