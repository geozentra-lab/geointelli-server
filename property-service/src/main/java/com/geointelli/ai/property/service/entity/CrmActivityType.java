package com.geointelli.ai.property.service.entity;

/** Events written for real CRM changes, or an explicitly submitted note. */
public enum CrmActivityType {
    CLIENT_CREATED, CLIENT_UPDATED, CLIENT_DELETED,
    LEAD_CREATED, LEAD_UPDATED, LEAD_STAGE_CHANGED, NOTE_ADDED,
    TASK_CREATED, TASK_UPDATED, TASK_STATUS_CHANGED, TASK_DELETED,
    TRANSACTION_CREATED, TRANSACTION_UPDATED, TRANSACTION_STAGE_CHANGED
}
