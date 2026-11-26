package com.geointelli.ai.property.service.entity;

/**
 * Group of a provider field in the listing field mapping (SCRUM-118). The groups follow the ticket's acceptance
 * criteria: identifiers, timestamps, status and asking price, with the completed-sale price kept in its own group
 * so the two prices are never confused.
 */
public enum ListingFieldGroup {
    IDENTIFIER,
    TIMESTAMP,
    STATUS,
    /** Price asked by the seller while the home is on the market. */
    ASKING_PRICE,
    /** Price of a completed sale as the provider knows it; cross-check only, the county sales table is authoritative. */
    SALE_PRICE,
    /** Address, coordinates and county, used to match a listing to one of our properties (SCRUM-120). */
    LOCATION,
    /** Beds, baths, size and type; secondary to the appraiser record. */
    DESCRIPTION,
    /** Provider fields we deliberately do not import, or that the provider does not have. */
    NOT_MAPPED
}
