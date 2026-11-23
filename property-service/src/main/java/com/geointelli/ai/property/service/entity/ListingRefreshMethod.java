package com.geointelli.ai.property.service.entity;

/** How new listing data arrives from a provider (SCRUM-118). */
public enum ListingRefreshMethod {
    /** We re-run the collection and re-import; a re-import updates the rows it already wrote. */
    BATCH_PULL,
    /** The provider pushes changes to us. */
    FEED
}
