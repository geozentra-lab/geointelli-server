package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;

/**
 * Returns true for a newly saved notification and false for an identical
 * redelivery.
 */
public interface AlertEventConsumptionService {
    boolean consume(DecodedAlertEventDTO event);
}
