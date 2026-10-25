package com.geointelli.ai.property.service.entity;

/**
 * Supported rule contracts for future evaluation.
 * NEW_MATCH means a newly matching property, not proof of a new listing.
 * Listing price changes require actual listing evidence; valuation changes require supported valuation evidence.
 */
public enum AlertEventType {
    NEW_MATCH, LISTING_PRICE_CHANGED, VALUATION_CHANGED
}
