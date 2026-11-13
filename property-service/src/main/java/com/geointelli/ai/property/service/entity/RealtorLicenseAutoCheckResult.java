package com.geointelli.ai.property.service.entity;

/** Outcome of an automatic registry check of a realtor license submission; informative only. */
public enum RealtorLicenseAutoCheckResult {
    /** The registry returned an active license whose holder matches the submitted name. */
    MATCH,
    /** The registry returned no such license, or a different holder or an inactive license. */
    NO_MATCH,
    /** The registry could not be queried or the answer was ambiguous; a human must decide. */
    INCONCLUSIVE
}
