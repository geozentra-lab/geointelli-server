package com.geointelli.ai.property.service.entity;

/** Events written for real CRM changes, or an explicitly submitted note. */
public enum CrmActivityType {
    CLIENT_CREATED, CLIENT_UPDATED, CLIENT_DELETED,
    LEAD_CREATED, LEAD_UPDATED, LEAD_STAGE_CHANGED, NOTE_ADDED
}
