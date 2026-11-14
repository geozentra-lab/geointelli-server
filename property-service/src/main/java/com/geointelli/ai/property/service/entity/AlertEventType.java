package com.geointelli.ai.property.service.entity;

/**
 * Rule contracts. NEW_MATCH and VALUATION_CHANGED have background evaluators.
 * NEW_MATCH means a newly matching property, not proof of a new listing.
 * Listing price changes require actual listing evidence; valuation changes
 * require supported valuation evidence.
 */
public enum AlertEventType {
    NEW_MATCH, LISTING_PRICE_CHANGED, VALUATION_CHANGED
}
