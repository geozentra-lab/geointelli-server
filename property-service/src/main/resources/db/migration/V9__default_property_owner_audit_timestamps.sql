-- Hibernate's many-to-many mapping inserts only property_id and owner_id.
-- Supply audit timestamps for each new link without changing the mapping.
ALTER TABLE property_owner
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
