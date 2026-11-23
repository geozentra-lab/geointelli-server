package com.geointelli.ai.property.service.entity;

/**
 * Whether a listing provider may feed the listings table (SCRUM-118). Only an {@link #AUTHORIZED} source is
 * imported by SCRUM-119; the product owner moves a source from {@link #PENDING} to {@link #AUTHORIZED}.
 */
public enum ListingAuthorizationStatus {
    /** Proposed and documented, awaiting the product owner's decision. */
    PENDING,
    /** May feed the listings table. */
    AUTHORIZED,
    /** No longer allowed; existing rows keep their provenance. */
    REVOKED
}
