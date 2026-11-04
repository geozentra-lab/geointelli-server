package com.geointelli.ai.notification.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.geointelli.ai.notification.service.service.EmailWorkerService;

/**
 * Polling discovers due database work; UTC cadence is enforced by persisted
 * next-email times.
 */
@Component
@ConditionalOnProperty(prefix = "notifications.email", name = "enabled", havingValue = "true")
public class NotificationEmailScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationEmailScheduler.class);
    private final EmailWorkerService worker;
    private final NotificationEmailProperties settings;

    public NotificationEmailScheduler(EmailWorkerService worker, NotificationEmailProperties settings) {
        this.worker = worker;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${notifications.email.poll-delay-millis:30000}", initialDelayString = "${notifications.email.poll-delay-millis:30000}")
    public void poll() {
        try {
            for (int step = 0; step < settings.getStepsPerTick(); step++)
                if (!worker.processNext())
                    break;
        } catch (RuntimeException failure) {
            LOG.error("Email worker iteration failed; committed attempts remain recoverable");
        }
    }
}
