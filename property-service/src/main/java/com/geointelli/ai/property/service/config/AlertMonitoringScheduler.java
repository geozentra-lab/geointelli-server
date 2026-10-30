package com.geointelli.ai.property.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.exception.AlertEvaluationException;
import com.geointelli.ai.property.service.service.AlertEvaluationService;
import com.geointelli.ai.property.service.service.AlertOutboxPublishingService;

/** Bounds each polling cycle while transactions remain on the service proxies. */
@Component
@ConditionalOnProperty(prefix="alerts", name="enabled", havingValue="true")
public class AlertMonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertMonitoringScheduler.class);
    private final AlertEvaluationService evaluation;
    private final AlertOutboxPublishingService publishing;
    private final AlertMonitoringProperties settings;
    public AlertMonitoringScheduler(AlertEvaluationService evaluation, AlertOutboxPublishingService publishing,
            AlertMonitoringProperties settings) {
        this.evaluation=evaluation; this.publishing=publishing; this.settings=settings;
    }
    @Scheduled(fixedDelayString="${alerts.poll-delay-millis:30000}")
    public void evaluate() {
        try {
            evaluation.prepare();
            for (int i=0; i<settings.getStepsPerTick(); i++) {
                try { if (!evaluation.processNextBatch()) break; }
                catch (AlertEvaluationException failure) { evaluation.recordFailure(failure.getRuleId()); }
            }
        } catch (RuntimeException failure) { log.warn("Alert evaluation polling failed: {}", failure.getClass().getSimpleName()); }
    }
    @Scheduled(fixedDelayString="${alerts.publisher.poll-delay-millis:5000}")
    public void publish() {
        if (!settings.getPublisher().isEnabled()) return;
        try {
            for (int i=0; i<settings.getPublisher().getBatchSize(); i++) {
                if (Thread.currentThread().isInterrupted() || !publishing.processNext()) break;
            }
        } catch (RuntimeException failure) { log.warn("Alert publishing polling failed: {}", failure.getClass().getSimpleName()); }
    }
}
