-- SCRUM-202: school reference data read by the next school ticket (zoned schools and their quality).
-- School locations, FLDOE school grades and attendance zones, loaded with the SCRUM-201 COPY loader and logged in
-- reference_dataset_loads (V42, required). PostgreSQL + PostGIS only. See docs/school-reference-data.md.
-- Apply by hand in ONE transaction (psql -1 -f ...). Adds three tables; changes no existing table or row.
--
-- Rule for every column: a value the source does not give is stored as NULL, which means UNKNOWN. An incomplete
-- or missing grade is NULL, never 0 or a low grade.

-- 1. Schools: one row per school of the loaded counties (public and charter, open and closed).
-- school_key is the stable key of the source file; rows are upserted on it, so a school keeps its id across reloads.
-- Closed schools stay, with is_active = false: keep them for history, never assign or score them.
CREATE TABLE schools (
    id BIGSERIAL PRIMARY KEY,
    school_key VARCHAR(80) NOT NULL,
    state_school_id VARCHAR(7),
    nces_id VARCHAR(20),
    fips5 CHAR(5) NOT NULL,
    county VARCHAR(80),
    name VARCHAR(200) NOT NULL,
    sector VARCHAR(10),
    level VARCHAR(12),
    school_type VARCHAR(20),
    lowest_grade SMALLINT,
    highest_grade SMALLINT,
    is_virtual BOOLEAN,
    is_active BOOLEAN NOT NULL,
    enrollment INTEGER,
    free_reduced_lunch_pct NUMERIC(5, 2),
    address VARCHAR(200),
    city VARCHAR(100),
    zip VARCHAR(10),
    in_schools_zip BOOLEAN,
    status_note VARCHAR(200),
    geom geometry(Point, 4326) NOT NULL,
    source_file VARCHAR(200) NOT NULL,
    source_row INTEGER NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_schools_school_key UNIQUE (school_key),
    CONSTRAINT ck_schools_fips5 CHECK (fips5 ~ '^[0-9]{5}$'),
    CONSTRAINT ck_schools_state_school_id CHECK (state_school_id ~ '^[0-9]{2}-[0-9]{4}$'),
    CONSTRAINT ck_schools_sector CHECK (sector IN ('PUBLIC', 'CHARTER')),
    CONSTRAINT ck_schools_level CHECK (level IN ('PRE_K', 'ELEMENTARY', 'MIDDLE', 'HIGH', 'COMBINATION')),
    CONSTRAINT ck_schools_school_type CHECK (school_type IN ('REGULAR', 'ALTERNATIVE', 'SPECIAL_EDUCATION', 'CAREER_TECHNICAL')),
    -- -1 = Pre-K, 0 = kindergarten, 1-12.
    CONSTRAINT ck_schools_grades CHECK (lowest_grade BETWEEN -1 AND 12 AND highest_grade BETWEEN -1 AND 12
        AND lowest_grade <= highest_grade),
    CONSTRAINT ck_schools_enrollment CHECK (enrollment >= 0),
    CONSTRAINT ck_schools_free_reduced_lunch_pct CHECK (free_reduced_lunch_pct BETWEEN 0 AND 100)
);
-- FLDOE id DD-SSSS (district + school), the key of school_grades; empty for schools FLDOE does not list yet.
CREATE UNIQUE INDEX ux_schools_state_school_id ON schools (state_school_id) WHERE state_school_id IS NOT NULL;
CREATE INDEX idx_schools_fips5 ON schools (fips5);
CREATE INDEX idx_schools_geom ON schools USING GIST (geom);
-- Nearest-school (KNN) and radius searches in metres (geography) use this index.
CREATE INDEX idx_schools_geog ON schools USING GIST ((geom::geography));

-- 2. FLDOE school grades: one row per school per graded year, statewide (all districts), 1999 onwards.
-- No foreign key to schools: grades cover every Florida district, schools only the loaded counties.
-- letter_grade is A-F, or NULL when FLDOE gave no letter: fldoe_grade keeps FLDOE's code as published
-- (I = incomplete, too few students tested; N (2000-2003) and P (2004-2007) are other FLDOE codes without a letter).
-- The components and pct_total_points exist only for the newest year of the FLDOE file.
CREATE TABLE school_grades (
    state_school_id VARCHAR(7) NOT NULL,
    school_year SMALLINT NOT NULL,
    district_number SMALLINT NOT NULL,
    school_number SMALLINT NOT NULL,
    school_name VARCHAR(200),
    letter_grade CHAR(1),
    fldoe_grade VARCHAR(2),
    is_baseline BOOLEAN NOT NULL,
    fldoe_school_type VARCHAR(12),
    is_charter BOOLEAN,
    is_title_i BOOLEAN,
    alternative_ese_center VARCHAR(20),
    grade3_ela_achievement NUMERIC(5, 2),
    ela_achievement NUMERIC(5, 2),
    ela_learning_gains NUMERIC(5, 2),
    ela_lowest25_gains NUMERIC(5, 2),
    math_achievement NUMERIC(5, 2),
    math_learning_gains NUMERIC(5, 2),
    math_lowest25_gains NUMERIC(5, 2),
    science_achievement NUMERIC(5, 2),
    social_studies_achievement NUMERIC(5, 2),
    middle_school_acceleration NUMERIC(5, 2),
    graduation_rate NUMERIC(5, 2),
    college_career_acceleration NUMERIC(5, 2),
    total_points_earned NUMERIC(7, 2),
    total_components SMALLINT,
    pct_total_points NUMERIC(5, 2),
    pct_tested NUMERIC(5, 2),
    pct_econ_disadvantaged NUMERIC(5, 2),
    -- 0-100 rank of pct_total_points among the schools of the same fldoe_school_type and school_year, computed
    -- after each load. NULL without pct_total_points or a letter grade (never 0 for "unknown").
    statewide_percentile SMALLINT,
    source_file VARCHAR(200) NOT NULL,
    source_row INTEGER NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (state_school_id, school_year),
    CONSTRAINT ck_school_grades_state_school_id CHECK (state_school_id ~ '^[0-9]{2}-[0-9]{4}$'),
    CONSTRAINT ck_school_grades_year CHECK (school_year BETWEEN 1990 AND 2100),
    CONSTRAINT ck_school_grades_letter CHECK (letter_grade IN ('A', 'B', 'C', 'D', 'F')),
    CONSTRAINT ck_school_grades_fldoe_grade CHECK (fldoe_grade IN ('A', 'B', 'C', 'D', 'F', 'I', 'N', 'P')),
    CONSTRAINT ck_school_grades_type CHECK (fldoe_school_type IN ('Elementary', 'Middle', 'High', 'Combination')),
    CONSTRAINT ck_school_grades_percents CHECK (pct_total_points BETWEEN 0 AND 100 AND pct_tested BETWEEN 0 AND 100
        AND pct_econ_disadvantaged BETWEEN 0 AND 100),
    CONSTRAINT ck_school_grades_percentile CHECK (statewide_percentile BETWEEN 0 AND 100)
);
CREATE INDEX idx_school_grades_year_type ON school_grades (school_year, fldoe_school_type);

-- 3. Attendance zones: one row per zone polygon of the loaded counties. A school can have several rows at one level
-- (separate parts of its zone), and zones of one level can overlap, so (school, level) is not unique.
-- school_key is the source's link to schools; school_id is resolved from it at load (and after every schools load)
-- and is NULL when the zone has no school (new or closed school missing from the list).
-- Each polygon is repaired at load: ST_Multi(ST_CollectionExtract(ST_MakeValid(...), 3)).
CREATE TABLE school_attendance_zones (
    id BIGSERIAL PRIMARY KEY,
    fips5 CHAR(5) NOT NULL,
    county VARCHAR(80),
    level VARCHAR(12) NOT NULL,
    school_key VARCHAR(80),
    school_id BIGINT REFERENCES schools (id) ON DELETE SET NULL,
    zone_school_name VARCHAR(200),
    school_match VARCHAR(20),
    source_school_id VARCHAR(40),
    source VARCHAR(500),
    source_type VARCHAR(30) NOT NULL,
    zone_year VARCHAR(80),
    method_fr VARCHAR(1000),
    note_fr VARCHAR(2000),
    geom geometry(MultiPolygon, 4326) NOT NULL,
    source_file VARCHAR(200) NOT NULL,
    source_row INTEGER NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_school_attendance_zones_fips5 CHECK (fips5 ~ '^[0-9]{5}$'),
    CONSTRAINT ck_school_attendance_zones_level CHECK (level IN ('ELEMENTARY', 'MIDDLE', 'HIGH', 'COMBINATION')),
    CONSTRAINT ck_school_attendance_zones_match CHECK (school_match IN ('state_school_id', 'nces_id', 'name', 'none')),
    -- How far the zone can be trusted, best first.
    CONSTRAINT ck_school_attendance_zones_source_type CHECK (source_type IN ('OFFICIAL', 'OFFICIAL_RECONSTRUCTED',
        'COUNTY_WIDE_SINGLE_SCHOOL', 'NCES_SABS_2015_16', 'UNOFFICIAL'))
);
CREATE INDEX idx_school_attendance_zones_geom ON school_attendance_zones USING GIST (geom);
CREATE INDEX idx_school_attendance_zones_fips5 ON school_attendance_zones (fips5, level);
CREATE INDEX idx_school_attendance_zones_school ON school_attendance_zones (school_id);
CREATE INDEX idx_school_attendance_zones_school_key ON school_attendance_zones (school_key);
