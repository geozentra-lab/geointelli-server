package com.geointelli.ai.property.service.entity;

/** Who decided a realtor license submission; null while the submission is pending. */
public enum RealtorLicenseDecisionMethod {
    /** A GeoZentra admin checked the state registry by hand (SCRUM-199). */
    MANUAL,
    /** A registry API answered without human review (future integration). */
    AUTOMATIC
}
