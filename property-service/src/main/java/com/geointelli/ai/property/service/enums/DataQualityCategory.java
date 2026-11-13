package com.geointelli.ai.property.service.enums;

/** Kinds of data-quality issues detected by the admin data-quality scan (SCRUM-189). */
public enum DataQualityCategory {
    /** A record that should exist is absent. */
    MISSING,
    /** A record exists but is too old to be trusted as current. */
    STALE,
    /** Two sources disagree on the same value. */
    CONFLICTING,
    /** A value is outside the plausible range. */
    OUTLIER,
    /** The same record is stored more than once. */
    DUPLICATE
}
