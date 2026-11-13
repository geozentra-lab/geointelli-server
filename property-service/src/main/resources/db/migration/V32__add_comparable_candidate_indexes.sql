-- Indexes for ComparableCandidateRepository.findCandidates (comparables and the valuation endpoint's
-- supporting comparables). Candidates are collected from the parent group, subdivision, neighborhood
-- and nearby parcels instead of scanning every same-type property in the county.
-- Built concurrently so ingestion jobs keep writing. A failed concurrent build leaves an
-- INVALID index that IF NOT EXISTS skips: drop it and re-run.

-- Same parent group, and child units of nearby parcels.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_properties_parent_property_id
    ON properties (parent_property_id) WHERE parent_property_id IS NOT NULL;

-- Same subdivision / neighborhood. The expressions must stay identical to the query's
-- LOWER(BTRIM(...)) matches so the indexes are used.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_properties_county_subdivision
    ON properties (county_id, LOWER(BTRIM(subdivision)));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_properties_county_neighborhood
    ON properties (county_id, LOWER(BTRIM(neighborhood)));

-- Usable parcel per property. The predicate must stay identical to the query's geometry checks so
-- PostgreSQL skips ST_IsValid on every candidate polygon at request time.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_parcels_valid_geom_property
    ON parcels (property_id, fid)
    WHERE geom IS NOT NULL AND NOT ST_IsEmpty(geom) AND ST_SRID(geom) = 4326 AND ST_IsValid(geom);
