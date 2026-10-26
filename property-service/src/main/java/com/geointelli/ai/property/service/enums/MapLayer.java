package com.geointelli.ai.property.service.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Static catalog of the map layers supported by the platform.
 * <p>
 * Only layers backed by verified geometry data are listed here.
 * Layer IDs are shared by all map-layer endpoints.
 */
@Getter
@RequiredArgsConstructor
public enum MapLayer {
    PARCELS("parcels", "Property parcels", "polygon", "Geozentra Parcel Data");

    private final String id;
    private final String name;
    private final String type;
    private final String source;
}

