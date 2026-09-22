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