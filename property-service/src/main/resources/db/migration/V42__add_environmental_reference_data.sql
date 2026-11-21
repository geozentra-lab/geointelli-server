-- SCRUM-201: environmental reference data read by the property risk context (SCRUM-127).
-- FEMA flood zones, census tracts, the FEMA National Risk Index per tract, NOAA storm events, the load history
-- (data vintage) and the county FIPS code that links them to counties. PostgreSQL + PostGIS only.
-- Apply by hand in ONE transaction (psql -1 -f ...): the county check at the end rolls everything back when a
-- Florida county name does not match. See docs/environmental-reference-data.md.
--
-- Rule for every column: a value the source does not give (empty, -9999, "No Rating", "Insufficient Data",
-- "Not Applicable") is stored as NULL, which means UNKNOWN. It is never replaced by a default or "low risk".

-- 1. County FIPS code (state 2 digits + county 3 digits), the key every source file uses.
ALTER TABLE counties ADD COLUMN fips_code CHAR(5);
ALTER TABLE counties ADD CONSTRAINT uq_counties_fips_code UNIQUE (fips_code);
ALTER TABLE counties ADD CONSTRAINT ck_counties_fips_code CHECK (fips_code ~ '^[0-9]{5}$');

-- Names compared without case, punctuation or spaces, with "Saint"/"St." alike:
-- "St. Johns" = "Saint Johns" = "St Johns", "DeSoto" = "De Soto", "Miami-Dade" = "Miami Dade".
UPDATE counties c
SET fips_code = v.fips
FROM states s,
     (VALUES
        ('12001', 'Alachua'), ('12003', 'Baker'), ('12005', 'Bay'), ('12007', 'Bradford'), ('12009', 'Brevard'),
        ('12011', 'Broward'), ('12013', 'Calhoun'), ('12015', 'Charlotte'), ('12017', 'Citrus'), ('12019', 'Clay'),
        ('12021', 'Collier'), ('12023', 'Columbia'), ('12027', 'DeSoto'), ('12029', 'Dixie'), ('12031', 'Duval'),
        ('12033', 'Escambia'), ('12035', 'Flagler'), ('12037', 'Franklin'), ('12039', 'Gadsden'),
        ('12041', 'Gilchrist'), ('12043', 'Glades'), ('12045', 'Gulf'), ('12047', 'Hamilton'), ('12049', 'Hardee'),
        ('12051', 'Hendry'), ('12053', 'Hernando'), ('12055', 'Highlands'), ('12057', 'Hillsborough'),
        ('12059', 'Holmes'), ('12061', 'Indian River'), ('12063', 'Jackson'), ('12065', 'Jefferson'),
        ('12067', 'Lafayette'), ('12069', 'Lake'), ('12071', 'Lee'), ('12073', 'Leon'), ('12075', 'Levy'),
        ('12077', 'Liberty'), ('12079', 'Madison'), ('12081', 'Manatee'), ('12083', 'Marion'), ('12085', 'Martin'),
        ('12086', 'Miami-Dade'), ('12087', 'Monroe'), ('12089', 'Nassau'), ('12091', 'Okaloosa'),
        ('12093', 'Okeechobee'), ('12095', 'Orange'), ('12097', 'Osceola'), ('12099', 'Palm Beach'),
        ('12101', 'Pasco'), ('12103', 'Pinellas'), ('12105', 'Polk'), ('12107', 'Putnam'), ('12109', 'St. Johns'),
        ('12111', 'St. Lucie'), ('12113', 'Santa Rosa'), ('12115', 'Sarasota'), ('12117', 'Seminole'),
        ('12119', 'Sumter'), ('12121', 'Suwannee'), ('12123', 'Taylor'), ('12125', 'Union'), ('12127', 'Volusia'),
        ('12129', 'Wakulla'), ('12131', 'Walton'), ('12133', 'Washington')
     ) AS v(fips, name)
WHERE s.id = c.state_id
  AND s.code = 'FL'
  AND regexp_replace(regexp_replace(lower(c.name), '^(saint|st\.?)\s+', 'st'), '[^a-z]', '', 'g')
    = regexp_replace(regexp_replace(lower(v.name), '^(saint|st\.?)\s+', 'st'), '[^a-z]', '', 'g');

DO $$
DECLARE
    unmatched TEXT;
    matched INTEGER;
BEGIN
    SELECT string_agg(c.name, ', ' ORDER BY c.name) INTO unmatched
    FROM counties c JOIN states s ON s.id = c.state_id
    WHERE s.code = 'FL' AND c.fips_code IS NULL;
    IF unmatched IS NOT NULL THEN
        RAISE EXCEPTION 'SCRUM-201: no FIPS code for the Florida counties: %', unmatched;
    END IF;
    SELECT count(*) INTO matched FROM counties WHERE fips_code IS NOT NULL;
    RAISE NOTICE 'SCRUM-201: FIPS code set on % counties', matched;
END $$;

-- 2. FEMA National Flood Hazard Layer, flood hazard zones (layer 28). One row per part of a zone polygon:
-- polygons are repaired and cut into parts of at most 256 vertices so point lookups stay fast. The parts of
-- one FEMA polygon share (source_file, source_row); FLD_AR_ID is not unique in FEMA's data, so id is a
-- surrogate key. A polygon with no area left after repair keeps one row with geom NULL.
CREATE TABLE flood_zones (
    id BIGSERIAL PRIMARY KEY,
    source_file VARCHAR(120) NOT NULL,
    source_row INTEGER NOT NULL,
    fips5 CHAR(5) NOT NULL,
    dfirm_id VARCHAR(20),
    fld_ar_id VARCHAR(40),
    fld_zone VARCHAR(30),
    zone_subty VARCHAR(100),
    sfha BOOLEAN,
    static_bfe NUMERIC(10, 2),
    depth NUMERIC(10, 2),
    velocity NUMERIC(10, 2),
    len_unit VARCHAR(20),
    vel_unit VARCHAR(20),
    v_datum VARCHAR(20),
    dual_zone VARCHAR(10),
    study_typ VARCHAR(20),
    version_id VARCHAR(20),
    source_cit VARCHAR(40),
    geom geometry(MultiPolygon, 4326),
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_flood_zones_geom ON flood_zones USING GIST (geom);
-- Re-importing a county replaces its rows; the parts of one FEMA polygon are regrouped by source row.
CREATE INDEX idx_flood_zones_fips5 ON flood_zones (fips5, source_row);

-- 3. Census TIGER/Line 2024 tract boundaries (2020-vintage tracts).
CREATE TABLE census_tracts (
    tract_fips CHAR(11) PRIMARY KEY,
    fips5 CHAR(5) NOT NULL,
    name VARCHAR(100),
    land_area_m2 BIGINT,
    water_area_m2 BIGINT,
    geom geometry(MultiPolygon, 4326) NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_census_tracts_fips CHECK (tract_fips ~ '^[0-9]{11}$' AND left(tract_fips, 5) = fips5)
);
CREATE INDEX idx_census_tracts_geom ON census_tracts USING GIST (geom);
CREATE INDEX idx_census_tracts_fips5 ON census_tracts (fips5);

-- 4. FEMA National Risk Index per census tract. Scores are 0-100 percentiles; ratings are FEMA's labels
-- ("Very Low" ... "Very High"). NULL score/rating = FEMA has no evidence. raw keeps every source column as text.
-- No foreign key to census_tracts: a few NRI and TIGER tracts differ, and either table can be refreshed alone.
CREATE TABLE nri_tract_risk (
    tract_fips CHAR(11) PRIMARY KEY,
    fips5 CHAR(5) NOT NULL,
    nri_version VARCHAR(40),
    risk_score NUMERIC(9, 6),
    risk_rating VARCHAR(30),
    hrcn_score NUMERIC(9, 6),
    hrcn_rating VARCHAR(30),
    ifld_score NUMERIC(9, 6),
    ifld_rating VARCHAR(30),
    cfld_score NUMERIC(9, 6),
    cfld_rating VARCHAR(30),
    trnd_score NUMERIC(9, 6),
    trnd_rating VARCHAR(30),
    wfir_score NUMERIC(9, 6),
    wfir_rating VARCHAR(30),
    swnd_score NUMERIC(9, 6),
    swnd_rating VARCHAR(30),
    ltng_score NUMERIC(9, 6),
    ltng_rating VARCHAR(30),
    hwav_score NUMERIC(9, 6),
    hwav_rating VARCHAR(30),
    raw JSONB NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_nri_tract_risk_fips CHECK (tract_fips ~ '^[0-9]{11}$' AND left(tract_fips, 5) = fips5)
);
CREATE INDEX idx_nri_tract_risk_fips5 ON nri_tract_risk (fips5);

-- 5. NOAA NCEI Storm Events. cz_type C = county (fips5 set), Z = forecast zone, M = marine zone (fips5 NULL).
-- begin_at/end_at are built from BEGIN_YEARMONTH/BEGIN_DAY/BEGIN_TIME and the recorded CZ_TIMEZONE offset.
-- Damage is in US dollars (NOAA's "25.00K", "1.5M"); NULL = not reported. geom = BEGIN_LAT/BEGIN_LON, NULL when
-- NOAA has no coordinates. Before 1996 only tornado, thunderstorm wind and hail were recorded.
CREATE TABLE storm_events (
    event_id BIGINT PRIMARY KEY,
    episode_id BIGINT,
    state_fips CHAR(2) NOT NULL,
    fips5 CHAR(5),
    cz_type CHAR(1) NOT NULL,
    cz_fips INTEGER,
    cz_name VARCHAR(200),
    event_type VARCHAR(50) NOT NULL,
    begin_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    cz_timezone VARCHAR(10),
    injuries_direct INTEGER,
    injuries_indirect INTEGER,
    deaths_direct INTEGER,
    deaths_indirect INTEGER,
    damage_property_usd NUMERIC(16, 2),
    damage_crops_usd NUMERIC(16, 2),
    magnitude NUMERIC(8, 2),
    magnitude_type VARCHAR(4),
    tor_f_scale VARCHAR(5),
    flood_cause VARCHAR(60),
    geom geometry(Point, 4326),
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_storm_events_cz_type CHECK (cz_type IN ('C', 'Z', 'M'))
);
CREATE INDEX idx_storm_events_geom ON storm_events USING GIST (geom);
-- Radius searches in kilometres (ST_DWithin on geography) use this index.
CREATE INDEX idx_storm_events_geog ON storm_events USING GIST ((geom::geography));
CREATE INDEX idx_storm_events_fips5 ON storm_events (fips5, begin_at);
CREATE INDEX idx_storm_events_state ON storm_events (state_fips);

-- 6. Load history: one row per imported file. The latest row per dataset (and per file for flood zones) is the
-- data vintage shown with a risk result.
CREATE TABLE reference_dataset_loads (
    id BIGSERIAL PRIMARY KEY,
    dataset VARCHAR(40) NOT NULL,
    source VARCHAR(300) NOT NULL,
    downloaded_on DATE NOT NULL,
    file VARCHAR(200) NOT NULL,
    fips5 CHAR(5),
    rows_read INTEGER NOT NULL,
    rows_loaded INTEGER NOT NULL,
    job_id UUID,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reference_dataset_loads_rows CHECK (rows_loaded >= 0 AND rows_loaded <= rows_read)
);
CREATE INDEX idx_reference_dataset_loads_dataset ON reference_dataset_loads (dataset, loaded_at DESC);
