-- SCRUM-133: economic indicators (SCRUM-55), read by the market outputs.
-- County employment from the BLS LAUS API and county population and migration from the Census Bureau population
-- estimates. PostgreSQL only. Requires V42 (counties.fips_code). See docs/economic-indicators.md.
-- V45 to V49 are taken by master and other branches, so this is V50.
-- Apply by hand in ONE transaction (psql -1 -f ...). Adds three tables; changes no existing table or row.
--
-- Rule: a value the source does not give is not stored. A missing month or year has no row; it is never 0.

-- 1. Indicator definitions: what each indicator is, its unit, frequency, geography and source.
CREATE TABLE economic_indicators (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    unit VARCHAR(10) NOT NULL,
    frequency VARCHAR(10) NOT NULL,
    geography_level VARCHAR(16) NOT NULL,
    source VARCHAR(20) NOT NULL,
    source_name VARCHAR(120) NOT NULL,
    source_url VARCHAR(300) NOT NULL,
    CONSTRAINT ck_economic_indicators_unit CHECK (unit IN ('PERSONS', 'PERCENT')),
    CONSTRAINT ck_economic_indicators_frequency CHECK (frequency IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_economic_indicators_geography_level CHECK (geography_level IN ('COUNTY')),
    CONSTRAINT ck_economic_indicators_source CHECK (source IN ('BLS_LAUS', 'CENSUS_PEP'))
);

INSERT INTO economic_indicators (code, name, description, unit, frequency, geography_level, source, source_name,
                                 source_url) VALUES
('LABOR_FORCE', 'Labor force',
 'People 16 and over living in the county who are employed or looking for work, monthly, not seasonally adjusted.',
 'PERSONS', 'MONTHLY', 'COUNTY', 'BLS_LAUS',
 'U.S. Bureau of Labor Statistics, Local Area Unemployment Statistics', 'https://www.bls.gov/lau/'),
('EMPLOYMENT', 'Employment',
 'Employed people living in the county, monthly, not seasonally adjusted.',
 'PERSONS', 'MONTHLY', 'COUNTY', 'BLS_LAUS',
 'U.S. Bureau of Labor Statistics, Local Area Unemployment Statistics', 'https://www.bls.gov/lau/'),
('UNEMPLOYMENT_RATE', 'Unemployment rate',
 'Unemployed people as a percent of the labor force, monthly, not seasonally adjusted.',
 'PERCENT', 'MONTHLY', 'COUNTY', 'BLS_LAUS',
 'U.S. Bureau of Labor Statistics, Local Area Unemployment Statistics', 'https://www.bls.gov/lau/'),
('POPULATION', 'Resident population',
 'Resident population on July 1 of the year.',
 'PERSONS', 'ANNUAL', 'COUNTY', 'CENSUS_PEP',
 'U.S. Census Bureau, Population Estimates Program', 'https://www.census.gov/programs-surveys/popest.html'),
('NET_MIGRATION', 'Net migration',
 'Net domestic plus net international migration from July 1 of the previous year to June 30 of the year.',
 'PERSONS', 'ANNUAL', 'COUNTY', 'CENSUS_PEP',
 'U.S. Census Bureau, Population Estimates Program', 'https://www.census.gov/programs-surveys/popest.html'),
('NET_DOMESTIC_MIGRATION', 'Net domestic migration',
 'People moving in from other U.S. counties minus people moving out, July 1 of the previous year to June 30.',
 'PERSONS', 'ANNUAL', 'COUNTY', 'CENSUS_PEP',
 'U.S. Census Bureau, Population Estimates Program', 'https://www.census.gov/programs-surveys/popest.html'),
('NET_INTERNATIONAL_MIGRATION', 'Net international migration',
 'People moving in from abroad minus people moving abroad, July 1 of the previous year to June 30.',
 'PERSONS', 'ANNUAL', 'COUNTY', 'CENSUS_PEP',
 'U.S. Census Bureau, Population Estimates Program', 'https://www.census.gov/programs-surveys/popest.html');

-- 2. Observations: ONE row per indicator, county and period. A new import of the same period updates the row
-- (revision) instead of adding one: previous_value keeps the value before the last change, revision_count counts
-- the changes. period_start/period_end: a month for monthly indicators; July 1 for the population; July 1 to June 30
-- for migration.
CREATE TABLE economic_indicator_observations (
    id BIGSERIAL PRIMARY KEY,
    indicator_code VARCHAR(40) NOT NULL REFERENCES economic_indicators (code),
    geography_level VARCHAR(16) NOT NULL,
    -- 5-digit county FIPS code; joins counties.fips_code (V42)
    geography_code VARCHAR(5) NOT NULL,
    geography_name VARCHAR(120),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    value NUMERIC(16, 2) NOT NULL,
    -- Same meaning as market_evidence_observations (SCRUM-131): ACTUAL = observed by the source;
    -- ESTIMATE = modelled or preliminary. BLS county LAUS values and Census population estimates are both
    -- model-based, so every value stored by SCRUM-133 is an ESTIMATE
    observation_kind VARCHAR(16) NOT NULL,
    source_series_id VARCHAR(40),
    -- BLS: date the API was read (yyyy-MM-dd); Census: vintage year. A row is only replaced by the same or a newer
    -- vintage, so re-importing an older Census file never undoes a revision.
    source_vintage VARCHAR(10) NOT NULL,
    source_url VARCHAR(300) NOT NULL,
    previous_value NUMERIC(16, 2),
    revision_count INTEGER NOT NULL DEFAULT 0,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revised_at TIMESTAMP WITH TIME ZONE,
    last_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_economic_indicator_observations
        UNIQUE (indicator_code, geography_level, geography_code, period_start),
    CONSTRAINT ck_economic_indicator_observations_level CHECK (geography_level IN ('COUNTY')),
    CONSTRAINT ck_economic_indicator_observations_code CHECK (geography_code ~ '^[0-9]{5}$'),
    CONSTRAINT ck_economic_indicator_observations_kind CHECK (observation_kind IN ('ACTUAL', 'ESTIMATE')),
    CONSTRAINT ck_economic_indicator_observations_period CHECK (period_end >= period_start),
    CONSTRAINT ck_economic_indicator_observations_revisions CHECK (revision_count >= 0)
);
CREATE INDEX idx_economic_indicator_observations_geography
    ON economic_indicator_observations (geography_code, indicator_code, period_start);

-- 3. Load history: one row per successful import, for freshness (when each source was last read).
CREATE TABLE economic_indicator_loads (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    source_url VARCHAR(300) NOT NULL,
    source_vintage VARCHAR(10) NOT NULL,
    state_fips CHAR(2) NOT NULL,
    period_from DATE,
    period_to DATE,
    received INTEGER NOT NULL,
    inserted INTEGER NOT NULL,
    revised INTEGER NOT NULL,
    unchanged INTEGER NOT NULL,
    older_vintage INTEGER NOT NULL,
    missing_values INTEGER NOT NULL,
    loaded_by VARCHAR(100),
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_economic_indicator_loads_source CHECK (source IN ('BLS_LAUS', 'CENSUS_PEP'))
);
CREATE INDEX idx_economic_indicator_loads_source ON economic_indicator_loads (source, loaded_at DESC);
