CREATE INDEX IF NOT EXISTS idx_properties_parent_property_id
    ON properties (parent_property_id);
