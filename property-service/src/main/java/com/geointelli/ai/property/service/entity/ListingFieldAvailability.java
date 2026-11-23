package com.geointelli.ai.property.service.entity;

/** How reliably a provider field is present, and whether we import it (SCRUM-118). */
public enum ListingFieldAvailability {
    /** Filled on (nearly) every record. */
    AVAILABLE,
    /** Filled on some records only, for example on listed homes but not on off-market ones. */
    PARTIAL,
    /** The provider does not give it. */
    UNAVAILABLE,
    /** The provider gives it and we do not import it, by decision (copyright, personal data, financing). */
    EXCLUDED;

    /** Whether a field with this availability is written to the listings table and therefore needs a target column. */
    public boolean isMapped() {
        return this == AVAILABLE || this == PARTIAL;
    }
}
