package com.geointelli.ai.property.service.entity;

/** Kind of rent evidence (SCRUM-132): advertised rent or rent actually paid. The two are never combined. */
public enum RentEvidenceType {
    /** Rent asked by landlords for homes offered for rent (listings). */
    ASKING,
    /** Rent paid by tenants under existing tenancies (census gross rent, registries, lease surveys). */
    ACHIEVED
}
