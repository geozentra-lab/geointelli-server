package com.geointelli.ai.property.service.repository;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.stereotype.Repository;

/** Loads only the bounded batch and parcel geometry needed by the worker. */
@Repository
public class ImageBackfillPropertyRepository {
    public PropertyGeometry load(Connection connection, long propertyId) throws SQLException {
        try (var query = connection.prepareStatement("""
            SELECT a.address, ST_AsText(parcel.geom) AS polygon,
                ST_XMin(bounds.box)||','||ST_YMin(bounds.box)||','||ST_XMax(bounds.box)||','||ST_YMax(bounds.box) AS bbox
            FROM properties p LEFT JOIN addresses a ON a.property_id=p.id
            LEFT JOIN LATERAL (
                SELECT ST_UnaryUnion(ST_Collect(pa.geom)) AS geom FROM parcels pa
                WHERE pa.property_id=p.id AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
                    AND ST_IsValid(pa.geom) AND ST_SRID(pa.geom)=4326
            ) parcel ON true
            LEFT JOIN LATERAL (SELECT ST_Envelope(ST_Buffer(parcel.geom::geography,80)::geometry) AS box) bounds ON true
            WHERE p.id=?
            """)) {
            query.setLong(1, propertyId); query.setQueryTimeout(60);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                return new PropertyGeometry(rows.getString("address"), rows.getString("polygon"), rows.getString("bbox"));
            }
        }
    }

    public record PropertyGeometry(String address, String polygon, String bbox) { }
}
