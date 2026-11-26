-- Rent evidence (SCRUM-132): one row per source, rent type, geography and observation period, imported from a
-- supported provider file and read by the rent statistics. Rows describe a market (a state, a county, a ZIP code
-- or a census tract), never one property or one lease.
--
-- ASKING rows are rents advertised by landlords (listings); ACHIEVED rows are rents actually paid by tenants
-- (contract or gross rent from a census, a registry or a lease survey). The type is part of the unique key and
-- of every read, so an advertised rent and a paid rent are never averaged together.
--
-- Every amount on a row is expressed per the row's rent_period (MONTHLY or ANNUAL), exactly as the provider
-- publishes it. No column holds a converted value: a reader that needs a monthly figure from an ANNUAL row
-- converts explicitly and says so. Values are the provider's own observations; nothing is estimated here.
CREATE TABLE rent_evidence_observations (
    id BIGSERIAL PRIMARY KEY,
    -- ASKING: advertised rent of homes offered for rent; ACHIEVED: rent paid under existing tenancies
    evidence_type VARCHAR(16) NOT NULL CHECK (evidence_type IN ('ASKING', 'ACHIEVED')),
    -- Provider code (for example US_CENSUS_ACS, HUD_FMR, ZILLOW_ZORI, REALTOR_COM_RENTALS)
    source VARCHAR(64) NOT NULL,
    -- Provider file, table or series the row came from, and the release it was taken from
    source_dataset VARCHAR(128),
    source_release_date DATE,
    -- Smallest geography the provider verifies for the row
    geography_level VARCHAR(16) NOT NULL CHECK (geography_level IN ('STATE', 'COUNTY', 'ZIP', 'TRACT')),
    -- STATE: two-letter code; COUNTY: five-digit FIPS; ZIP: five digits; TRACT: eleven-digit tract FIPS
    geography_code VARCHAR(32) NOT NULL,
    geography_name VARCHAR(120),
    -- Resolved against our states and counties when the code or name matches; NULL when it does not
    state_id BIGINT REFERENCES states(id),
    county_id BIGINT REFERENCES counties(id),
    -- Inclusive observation period: a month, a quarter, a year or a multi-year survey window (ACS 5-year)
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    -- ACTUAL: observed by the provider; ESTIMATE: a provider value marked as modelled, preliminary or a survey estimate
    observation_kind VARCHAR(16) NOT NULL DEFAULT 'ACTUAL' CHECK (observation_kind IN ('ACTUAL', 'ESTIMATE')),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    -- Unit of every amount on this row. Never mixed: a reader converts explicitly (ANNUAL / 12) and flags it.
    rent_period VARCHAR(8) NOT NULL CHECK (rent_period IN ('MONTHLY', 'ANNUAL')),
    -- Number of bedrooms the figures refer to; NULL = all sizes together
    bedrooms SMALLINT CHECK (bedrooms >= 0),
    -- Amounts, per rent_period, in currency
    median_rent NUMERIC(12,2) CHECK (median_rent >= 0),
    average_rent NUMERIC(12,2) CHECK (average_rent >= 0),
    median_rent_per_sqft NUMERIC(10,4) CHECK (median_rent_per_sqft >= 0),
    -- Provider's margin of error on median_rent (same unit), when published (ACS)
    median_rent_moe NUMERIC(12,2) CHECK (median_rent_moe >= 0),
    -- Listings, leases or survey units behind the figures
    sample_count INTEGER CHECK (sample_count >= 0),
    -- Provenance of the import itself
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    import_job_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_rent_evidence_period CHECK (period_start <= period_end),
    -- A row must carry at least one rent figure
    CONSTRAINT ck_rent_evidence_has_amount CHECK (
        median_rent IS NOT NULL OR average_rent IS NOT NULL OR median_rent_per_sqft IS NOT NULL),
    -- Idempotent imports: the same observation from the same source is one row, updated in place.
    -- NULLS NOT DISTINCT (PostgreSQL 15+) makes two "all sizes" rows (bedrooms NULL) count as duplicates too.
    CONSTRAINT uq_rent_evidence_observation UNIQUE NULLS NOT DISTINCT (
        source, evidence_type, geography_level, geography_code, period_start, period_end, rent_period, bedrooms)
);

-- Reading one market over time: every period of a tract, ZIP, county or state, one rent type
CREATE INDEX idx_rent_evidence_geography_period
    ON rent_evidence_observations (geography_level, geography_code, evidence_type, period_start);
-- Reading by our own county or state IDs, newest period first
CREATE INDEX idx_rent_evidence_county_period
    ON rent_evidence_observations (county_id, evidence_type, period_start DESC) WHERE county_id IS NOT NULL;
CREATE INDEX idx_rent_evidence_state_period
    ON rent_evidence_observations (state_id, evidence_type, period_start DESC) WHERE state_id IS NOT NULL;
