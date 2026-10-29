package com.geointelli.ai.property.service.service;

/** Publishes committed events independently from property evaluation. */
public interface AlertOutboxPublishingService {
    boolean processNext();
}
