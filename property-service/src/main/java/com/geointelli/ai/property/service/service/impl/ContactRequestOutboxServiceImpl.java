package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.config.ContactRequestProperties;
import com.geointelli.ai.property.service.repository.ContactRequestOutboxRepository;
import com.geointelli.ai.property.service.service.ContactRequestEventPublisher;
import com.geointelli.ai.property.service.service.ContactRequestOutboxService;
import lombok.RequiredArgsConstructor;

/** At-least-once delivery: a crash after the confirm can replay, and the consumer deduplicates eventId. */
@Service
@RequiredArgsConstructor
public class ContactRequestOutboxServiceImpl implements ContactRequestOutboxService {
    private final ContactRequestOutboxRepository outbox;
    private final ContactRequestEventPublisher publisher;
    private final ContactRequestProperties settings;

    @Override
    @Transactional(timeout = 45)
    public boolean processNext() {
        if (!settings.isPublisherEnabled()) return false;
        var row = outbox.lockNext(Instant.now()).orElse(null);
        if (row == null) return false;
        try {
            publisher.publish(row.getEventId(), row.getEventJson());
            row.setStatus("SENT");
            row.setSentAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            row.setLastErrorCode(null);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            row.setAttempts(row.getAttempts() + 1);
            row.setLastErrorCode("BROKER_DELIVERY_FAILED");
            row.setStatus(row.getAttempts() >= settings.getMaxAttempts() ? "FAILED" : "PENDING");
            long delay = Math.min(3600L, settings.getRetryBaseSeconds() * (1L << Math.min(10, row.getAttempts() - 1)));
            row.setNextAttemptAt(Instant.now().plusSeconds(delay).truncatedTo(ChronoUnit.MICROS));
        }
        return true;
    }
}

