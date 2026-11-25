package com.geointelli.ai.property.service.entity;

/** Kind of market evidence: closed transactions or homes offered for sale. The two are never combined. */
public enum MarketEvidenceType {
    /** Closed sales: counts and sold prices. */
    SALE,
    /** Inventory: active and new listings, asking prices, days on market. */
    ASKING
}
