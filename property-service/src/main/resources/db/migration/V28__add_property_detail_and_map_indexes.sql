-- Foreign-key indexes for loading a property's detail collections. Without them every
-- property page sequentially scans each multi-million-row child table.
-- Built concurrently so ingestion keeps writing. A failed concurrent build leaves an
-- INVALID index that IF NOT EXISTS skips: drop it and re-run.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_extra_features_property_roll_year
    ON extra_features (property_id, roll_year);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_buildings_property_id
    ON buildings (property_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lands_property_id
    ON lands (property_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_taxes_property_id
    ON taxes (property_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_assessments_property_id
    ON assessments (property_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_property_owner_property_id
    ON property_owner (property_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_property_owner_owner_id
    ON property_owner (owner_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_property_value_predictions_property_id
    ON property_value_predictions (property_id);

-- Map layer summary: MAX(updated_at) and the mapped-parcel count over parcels with geometry.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_parcels_mapped_updated_at
    ON parcels (updated_at) WHERE geom IS NOT NULL;
