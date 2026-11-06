-- Indexes for GET /api/properties/search filters. Built concurrently so ingestion keeps writing.
-- A failed concurrent build leaves an INVALID index that IF NOT EXISTS skips: drop it and re-run.

-- Map bounding-box filter (ST_Intersects on parcel geometry).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_parcels_geom
    ON parcels USING gist (geom);

-- Search's "address contains" filter (ILIKE '%text%') and address suggestions (<% word similarity).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_address_trgm
    ON addresses USING gin (address gin_trgm_ops);

-- Case-insensitive exact-match filters.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_lower_city
    ON addresses (LOWER(city));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_lower_zip
    ON addresses (LOWER(zip));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_properties_lower_neighborhood
    ON properties (LOWER(neighborhood));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_properties_lower_neighborhood_description
    ON properties (LOWER(neighborhood_description));
