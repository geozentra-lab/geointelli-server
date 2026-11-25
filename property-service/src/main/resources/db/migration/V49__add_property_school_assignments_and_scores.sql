-- SCRUM-203: the schools each property is zoned for, and school score v2 (FLDOE quality, access, choice).
-- Written by the school scoring jobs, one county per transaction, from the SCRUM-202 tables (V44, required).
-- PostgreSQL + PostGIS only. See docs/school-score-v2.md.
-- Apply by hand in ONE transaction (psql -1 -f ...). Adds two tables; changes no existing table or row.
-- property_school_analysis is left as it is: its v1 score and its rows (the sales of the ML model) do not change.
--
-- Rule for every column: a value that cannot be known is stored as NULL, never 0.

-- 1. One row per property and level: the school whose attendance zone contains the property (method ZONE); a zone
-- whose school is not in the school data (method UNMATCHED_ZONE: no school, the level is left out of the score, never
-- guessed); or, when no zone contains the property, the nearest zoned public school of the county (method NEAREST).
-- Every property with a parcel in a county with attendance zones gets its rows; a level with no school at all has no
-- row.
CREATE TABLE property_school_assignments (
    property_id BIGINT NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    level VARCHAR(12) NOT NULL,
    -- A schools reload deletes the schools missing from the new file, with their assignments: assign the county again.
    -- NULL for UNMATCHED_ZONE only.
    school_id BIGINT REFERENCES schools (id) ON DELETE CASCADE,
    method VARCHAR(14) NOT NULL,
    -- A zones reload replaces the county's zones (new ids): zone_id becomes NULL, zone_source_type stays, and the
    -- county should be assigned again.
    zone_id BIGINT REFERENCES school_attendance_zones (id) ON DELETE SET NULL,
    zone_source_type VARCHAR(30),
    -- From a point on the property's largest parcel to the school, in metres on the spheroid (geography); NULL for
    -- UNMATCHED_ZONE (no school point).
    distance_m NUMERIC(10, 1),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (property_id, level),
    CONSTRAINT ck_property_school_assignments_level CHECK (level IN ('ELEMENTARY', 'MIDDLE', 'HIGH')),
    CONSTRAINT ck_property_school_assignments_method CHECK (method IN ('ZONE', 'UNMATCHED_ZONE', 'NEAREST')),
    CONSTRAINT ck_property_school_assignments_school CHECK ((method = 'UNMATCHED_ZONE') = (school_id IS NULL)),
    CONSTRAINT ck_property_school_assignments_distance CHECK ((school_id IS NULL) = (distance_m IS NULL)
        AND distance_m >= 0),
    CONSTRAINT ck_property_school_assignments_zone CHECK (method <> 'NEAREST' OR zone_id IS NULL),
    CONSTRAINT ck_property_school_assignments_zone_source CHECK ((method = 'NEAREST') = (zone_source_type IS NULL)),
    CONSTRAINT ck_property_school_assignments_source_type CHECK (zone_source_type IN ('OFFICIAL',
        'OFFICIAL_RECONSTRUCTED', 'COUNTY_WIDE_SINGLE_SCHOOL', 'NCES_SABS_2015_16', 'UNOFFICIAL'))
);
-- Used by the foreign keys when a schools or zones reload deletes rows, and to list the properties of a school.
CREATE INDEX idx_property_school_assignments_school ON property_school_assignments (school_id);
CREATE INDEX idx_property_school_assignments_zone ON property_school_assignments (zone_id);

-- 2. One row per assigned property: school score v2 (0-100) and its three parts. A part that cannot be known (no
-- graded school) is NULL and the score is the weighted average of the known parts only.
CREATE TABLE property_school_scores (
    property_id BIGINT PRIMARY KEY REFERENCES properties (id) ON DELETE CASCADE,
    quality_score NUMERIC(5, 2),
    access_score NUMERIC(5, 2),
    choice_score NUMERIC(5, 2),
    school_score NUMERIC(5, 2),
    label VARCHAR(10),
    -- FLDOE school year (spring of the year) whose grades the quality and choice parts are based on.
    grade_year SMALLINT,
    score_version SMALLINT NOT NULL,
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_property_school_scores_range CHECK (quality_score BETWEEN 0 AND 100
        AND access_score BETWEEN 0 AND 100 AND choice_score BETWEEN 0 AND 100 AND school_score BETWEEN 0 AND 100),
    CONSTRAINT ck_property_school_scores_label CHECK (label IN ('Excellent', 'Good', 'Fair', 'Limited')),
    CONSTRAINT ck_property_school_scores_label_score CHECK ((label IS NULL) = (school_score IS NULL)),
    CONSTRAINT ck_property_school_scores_grade_year CHECK (grade_year BETWEEN 1990 AND 2100),
    CONSTRAINT ck_property_school_scores_version CHECK (score_version >= 2)
);
