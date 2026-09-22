CREATE TABLE property_import_mapping (
    id BIGSERIAL PRIMARY KEY,

    source_county VARCHAR(100) NOT NULL,
    source_state VARCHAR(50) NOT NULL,
    source_property_id BIGINT NOT NULL,

    geozentra_property_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_property_import_mapping
        UNIQUE (
            source_county,
            source_state,
            source_property_id
        ),

    CONSTRAINT fk_property_import_mapping_property
        FOREIGN KEY (geozentra_property_id)
        REFERENCES properties(id)
);

ALTER TABLE property_import_mapping ADD COLUMN created_at TIMESTAMP;
ALTER TABLE property_import_mapping ADD COLUMN updated_at TIMESTAMP;

UPDATE property_import_mapping SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;

ALTER TABLE property_import_mapping ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE property_import_mapping ALTER COLUMN updated_at SET NOT NULL;