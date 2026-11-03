package com.geointelli.ai.notification.service.service;

import org.springframework.amqp.core.Message;

/** Returns only after a persistent recovery copy is confirmed by the broker. */
public interface DeadLetterService {
    void publish(Message original, String reason, int attempts);
}
