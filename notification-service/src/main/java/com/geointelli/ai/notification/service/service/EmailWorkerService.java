package com.geointelli.ai.notification.service.service;

/** Processes at most one queued batch per invocation. */
public interface EmailWorkerService {
    boolean processNext();
}
