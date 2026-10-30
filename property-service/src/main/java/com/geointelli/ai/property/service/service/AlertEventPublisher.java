package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
/** Delivers an event only when the broker confirms its routing. */
public interface AlertEventPublisher {
    void publish(AlertEventEnvelopeDTO event) throws Exception;
}
