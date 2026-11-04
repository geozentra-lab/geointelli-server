package com.geointelli.ai.property.service.entity;

/** Manual property-deal tracking; closing does not perform a financial or legal transaction. */
public enum CrmTransactionStage {
    PROSPECT, VIEWING, OFFER, UNDER_CONTRACT, CLOSED, CANCELLED;

    public boolean canChangeTo(CrmTransactionStage next) {
        if (next == null) return false;
        if (this == next) return true;
        return switch (this) {
            case PROSPECT -> next == VIEWING || next == CANCELLED;
            case VIEWING -> next == OFFER || next == CANCELLED;
            case OFFER -> next == UNDER_CONTRACT || next == CANCELLED;
            case UNDER_CONTRACT -> next == CLOSED || next == CANCELLED;
            case CLOSED, CANCELLED -> false;
        };
    }
}
