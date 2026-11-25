package com.geointelli.ai.property.service.entity;

/** Whether the provider observed the value or marked it as modelled or preliminary. */
public enum MarketObservationKind {
    /** Observed by the provider from recorded transactions or listings. */
    ACTUAL,
    /** Published by the provider as an estimate, a model output or a preliminary figure. */
    ESTIMATE
}
