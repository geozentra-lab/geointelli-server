package com.geointelli.ai.notification.service.service;

import java.time.Instant;
import com.geointelli.ai.notification.service.entity.EmailCadence;

/**
 * Calculates UTC digest boundaries independently of saved-search evaluation
 * cadence.
 */
public interface EmailScheduleService {
    Instant next(EmailCadence cadence, Instant now);
}
