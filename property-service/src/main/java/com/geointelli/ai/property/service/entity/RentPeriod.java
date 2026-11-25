package com.geointelli.ai.property.service.entity;

/**
 * Unit of every amount on a rent observation (SCRUM-132). A MONTHLY and an ANNUAL figure are never added or
 * compared as they are; a reader that needs one unit converts explicitly (ANNUAL / 12) and says so.
 */
public enum RentPeriod {
    /** Amounts are per month. */
    MONTHLY,
    /** Amounts are per year. */
    ANNUAL
}
