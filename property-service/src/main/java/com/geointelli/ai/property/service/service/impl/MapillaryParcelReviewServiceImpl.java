package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.MapillaryParcelReviewService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses linked parcels and nearby addresses as evidence without altering source geography. */
@Service
public final class MapillaryParcelReviewServiceImpl implements MapillaryParcelReviewService {
    @Override
    public Map<String, Object> inspect(Connection connection, long propertyId) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
            WITH subject AS (
                SELECT p.id, p.folio, a.address, ST_UnaryUnion(ST_Collect(pa.geom)) AS geom,
                       count(pa.fid) AS parcel_count,
                       count(pa.fid) FILTER (WHERE pa.folio IS NOT NULL AND p.folio IS NOT NULL
                           AND trim(pa.folio) <> trim(p.folio)) AS folio_mismatches
                FROM properties p LEFT JOIN addresses a ON a.property_id=p.id
                LEFT JOIN parcels pa ON pa.property_id=p.id AND pa.geom IS NOT NULL
                    AND ST_IsValid(pa.geom) AND NOT ST_IsEmpty(pa.geom) AND ST_SRID(pa.geom)=4326
                WHERE p.id=? GROUP BY p.id,p.folio,a.address
            )
            SELECT id,folio,address,parcel_count,folio_mismatches,ST_AsGeoJSON(geom) AS geometry,
                   ST_Area(geom::geography) AS area FROM subject
            """)) {
            statement.setLong(1, propertyId); statement.setQueryTimeout(60);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Property not found for parcel review");
                result.put("address", rows.getString("address"));
                result.put("folio", rows.getString("folio"));
                result.put("linkedParcelCount", rows.getInt("parcel_count"));
                result.put("folioMismatches", rows.getInt("folio_mismatches"));
                result.put("geometryGeoJson", rows.getString("geometry"));
                result.put("areaSquareMeters", rows.getObject("area"));
            }
        }
        var neighbors = new ArrayList<Map<String, Object>>();
        try (var statement = connection.prepareStatement("""
            WITH subject AS MATERIALIZED (
                SELECT ST_UnaryUnion(ST_Collect(geom)) AS geom FROM parcels
                WHERE property_id=? AND geom IS NOT NULL AND ST_IsValid(geom)
                    AND NOT ST_IsEmpty(geom) AND ST_SRID(geom)=4326
            ), bounds AS MATERIALIZED (
                SELECT ST_Envelope(ST_Buffer(geom::geography,50)::geometry) AS box FROM subject
            ), nearby AS MATERIALIZED (
                SELECT pa.property_id,pa.geom FROM parcels pa CROSS JOIN bounds b
                WHERE pa.geom && b.box AND pa.property_id IS NOT NULL AND pa.property_id<>?
                    AND ST_IsValid(pa.geom) AND NOT ST_IsEmpty(pa.geom) AND ST_SRID(pa.geom)=4326
                ORDER BY pa.property_id,pa.fid LIMIT 100
            ), combined AS MATERIALIZED (
                SELECT property_id,ST_UnaryUnion(ST_Collect(geom)) AS geom
                FROM nearby GROUP BY property_id
            )
            SELECT p.id,p.folio,a.address,ST_AsGeoJSON(c.geom) AS geometry,
                ST_Area(ST_Intersection(c.geom,s.geom)::geography)
                    / NULLIF(ST_Area(s.geom::geography),0) AS overlap_fraction
            FROM combined c JOIN properties p ON p.id=c.property_id
            LEFT JOIN addresses a ON a.property_id=p.id CROSS JOIN subject s
            WHERE ST_DWithin(c.geom::geography,s.geom::geography,50)
            ORDER BY ST_Distance(c.geom::geography,s.geom::geography),p.id LIMIT 30
            """)) {
            statement.setLong(1, propertyId); statement.setLong(2, propertyId); statement.setQueryTimeout(60);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, Object> neighbor = new LinkedHashMap<>();
                    neighbor.put("propertyId", rows.getLong("id")); neighbor.put("folio", rows.getString("folio"));
                    neighbor.put("address", rows.getString("address")); neighbor.put("geometryGeoJson", rows.getString("geometry"));
                    neighbor.put("subjectOverlapFraction", rows.getDouble("overlap_fraction"));
                    neighbors.add(neighbor);
                }
            }
        }
        result.put("neighbors", neighbors);
        long shared = neighbors.stream().filter(n -> ((Number)n.get("subjectOverlapFraction")).doubleValue() > 0.8).count();
        result.put("substantiallyOverlappingProperties", shared);
        result.put("associationDecision", result.get("geometryGeoJson") == null ? "missing-geometry"
            : ((Number) result.get("folioMismatches")).intValue() > 0 || shared > 0
                ? "association-needs-review" : "linked-parcel-consistent-house-identity-unverified");
        result.put("neighborLimit", 30);
        result.put("neighborParcelRowLimit", 100);
        result.put("buildingFootprintAvailable", false);
        return result;
    }
}
