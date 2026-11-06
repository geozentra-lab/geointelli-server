package com.geointelli.ai.property.service.service;

/** Publishes immutable JSON and returns only after a broker confirm and successful routing. */
public interface ContactRequestEventPublisher {
    void publish(String eventId, String eventJson) throws Exception;
}

