package com.geointelli.ai.property.service.entity;

/** Recorded tracking stages; these do not approve or originate an actual loan. */
public enum LenderApplicationStage {
    DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, FUNDED, REJECTED, WITHDRAWN;

    public boolean canChangeTo(LenderApplicationStage next) {
        if (this == next) return true;
        if (this == FUNDED || this == REJECTED || this == WITHDRAWN) return false;
        if (next == WITHDRAWN) return true;
        return switch (this) {
            case DRAFT -> next == SUBMITTED;
            case SUBMITTED -> next == UNDER_REVIEW;
            case UNDER_REVIEW -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == FUNDED;
            default -> false;
        };
    }
}

