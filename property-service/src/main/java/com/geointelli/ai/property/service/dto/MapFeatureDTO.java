package com.geointelli.ai.property.service.dto;

/**
 * Geographic feature of a map layer.
 *
 * @param id       feature identifier (parcel ID)
 * @param folio    parcel folio number
 * @param geometry feature geometry (type and coordinates, WGS84)
 */
public record MapFeatureDTO(
        Long id,
        String folio,
        GeometryDTO geometry
) {
}
