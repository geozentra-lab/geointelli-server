package com.geointelli.ai.property.service.enums;

/**
 * Normalized transport categories of the location intelligence (SCRUM-126). Each one maps a distance
 * column of the stored transportation analysis to a stable, provider-independent name.
 */
public enum TransportCategory {
    /** Metrorail station ({@code dist_metro_rail}). */
    RAIL,
    /** Metromover station ({@code dist_metro_mover}). */
    PEOPLE_MOVER,
    /** Bus stop ({@code dist_bus_stop}). */
    BUS,
    /** Highway access ({@code dist_highway}). */
    HIGHWAY
}
