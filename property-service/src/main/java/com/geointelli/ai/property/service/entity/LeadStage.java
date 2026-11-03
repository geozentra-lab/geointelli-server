package com.geointelli.ai.property.service.entity;

/** Supported lead progression. CONVERTED and LOST are terminal stages. */
public enum LeadStage {
    NEW, CONTACTED, QUALIFIED, CONVERTED, LOST;

    /** Allows no-op updates, forward progression and closing an open lead as lost. */
    public boolean canChangeTo(LeadStage next) {
        if (next == null) return false;
        if (this == next) return true;
        return switch (this) {
            case NEW -> next == CONTACTED || next == LOST;
            case CONTACTED -> next == QUALIFIED || next == LOST;
            case QUALIFIED -> next == CONVERTED || next == LOST;
            case CONVERTED, LOST -> false;
        };
    }
}
