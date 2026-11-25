package com.geointelli.ai.property.service.entity;

/** Smallest geography a provider verifies for an observation; the code format depends on the level. */
public enum MarketGeographyLevel {
    /** Two-letter state code, for example {@code FL}. */
    STATE,
    /** Five-digit county FIPS code, for example {@code 12086} for Miami-Dade. */
    COUNTY,
    /** Five-digit ZIP code. */
    ZIP
}
