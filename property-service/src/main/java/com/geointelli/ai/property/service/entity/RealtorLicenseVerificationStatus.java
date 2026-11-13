package com.geointelli.ai.property.service.entity;

/** Outcome of the manual registry check of a realtor license submission; a decided row never changes again. */
public enum RealtorLicenseVerificationStatus {
    /** Submitted by the user and waiting for a GeoZentra admin to check the state registry. */
    PENDING_REVIEW,
    /** The admin found a matching name and an active license in the registry. */
    VERIFIED,
    /** The admin could not confirm the license; the user may submit corrected details as a new row. */
    REJECTED
}
