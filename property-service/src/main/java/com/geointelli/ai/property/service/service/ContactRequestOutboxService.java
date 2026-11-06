package com.geointelli.ai.property.service.service;

/** Processes one committed event, leaving stable event IDs intact across retry attempts. */
public interface ContactRequestOutboxService {
    boolean processNext();
}

