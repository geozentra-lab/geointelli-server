CREATE TABLE property_school_analysis (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL UNIQUE,

    dist_public_school DOUBLE PRECISION,
    dist_charter_school DOUBLE PRECISION,
    schools_within_1km INTEGER,

    public_school_distance_score DOUBLE PRECISION,
    charter_school_distance_score DOUBLE PRECISION,
    school_density_score DOUBLE PRECISION,

    school_score DOUBLE PRECISION,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO property_school_analysis (
    property_id,
    dist_public_school,
    dist_charter_school,
    schools_within_1km
)
SELECT DISTINCT ON (property_id)
    property_id,
    dist_public_school,
    dist_charter_school,
    schools_within_1km
FROM sales_ml_final
ORDER BY property_id, sale_date DESC;

UPDATE property_school_analysis SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;

ALTER TABLE property_school_analysis ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE property_school_analysis ALTER COLUMN updated_at SET NOT NULL;