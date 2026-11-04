package com.geointelli.ai.property.service.entity;

/** Task progress is independent of the linked transaction's stage. */
public enum CrmTaskStatus {
    OPEN, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canChangeTo(CrmTaskStatus next) {
        if (next == null) return false;
        if (this == next) return true;
        return switch (this) {
            case OPEN -> next == IN_PROGRESS || next == COMPLETED || next == CANCELLED;
            case IN_PROGRESS -> next == OPEN || next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> next == OPEN;
        };
    }

    public boolean isActive() { return this == OPEN || this == IN_PROGRESS; }
}
