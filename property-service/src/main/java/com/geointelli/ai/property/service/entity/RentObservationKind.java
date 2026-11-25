package com.geointelli.ai.property.service.entity;

/** How the provider obtained a rent figure (SCRUM-132). */
public enum RentObservationKind {
    /** Observed directly by the provider (listings, leases, registry). */
    ACTUAL,
    /** A survey estimate (with a margin of error), a modelled or a preliminary value. */
    ESTIMATE
}
