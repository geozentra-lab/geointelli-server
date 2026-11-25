-- Market transaction and inventory evidence (SCRUM-131): one row per source, evidence type, geography and
-- observation period, imported from an authorized provider file and read by the price and inventory
-- statistics. Rows describe a market (a state, a county or a ZIP code), never one property: per-property
-- sales stay in the sales table.
--
-- SALE rows carry the transaction columns (sale_*); ASKING rows carry the listing columns (*_listing_*,
-- *_asking_*, days on market). The check constraints keep the other group NULL, so a sold price and an asking
-- price can never be added together. Values are the provider's own observations; nothing is estimated here.
--
-- Apply by hand in ONE transaction (psql -1 -f ...), after V42: COUNTY rows are linked to our counties through
-- counties.fips_code. Adds one table; changes no existing table or row. See docs/market-evidence-ingestion.md.
CREATE TABLE market_evidence_observations (
    id BIGSERIAL PRIMARY KEY,
    -- SALE: closed transactions; ASKING: homes offered for sale (inventory and asking prices)
    evidence_type VARCHAR(16) NOT NULL CHECK (evidence_type IN ('SALE', 'ASKING')),
    -- Provider code (for example REALTOR_COM_RESEARCH, REDFIN_DATA_CENTER, COUNTY_APPRAISER_SALES)
    source VARCHAR(64) NOT NULL,
    -- Provider file or dataset the row came from, and the release it was taken from
    source_dataset VARCHAR(128),
    source_release_date DATE,
    -- Smallest geography the provider verifies for the row
    geography_level VARCHAR(16) NOT NULL CHECK (geography_level IN ('STATE', 'COUNTY', 'ZIP')),
    -- STATE: two-letter code; COUNTY: five-digit FIPS code; ZIP: five-digit ZIP code
    geography_code VARCHAR(32) NOT NULL,
    geography_name VARCHAR(120),
    -- Resolved against our states and counties when the code, FIPS code or name matches; NULL when it does not
    state_id BIGINT REFERENCES states(id),
    county_id BIGINT REFERENCES counties(id),
    -- Inclusive observation period, usually one calendar month
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    -- ACTUAL: observed by the provider; ESTIMATE: a provider value marked as modelled or preliminary
    observation_kind VARCHAR(16) NOT NULL DEFAULT 'ACTUAL' CHECK (observation_kind IN ('ACTUAL', 'ESTIMATE')),
    -- VARCHAR, not CHAR: the entity maps a String of length 3, which Hibernate validates as varchar(3)
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
    -- SALE columns
    sale_count INTEGER CHECK (sale_count >= 0),
    median_sale_price NUMERIC(14,2) CHECK (median_sale_price >= 0),
    average_sale_price NUMERIC(14,2) CHECK (average_sale_price >= 0),
    median_sale_price_per_sqft NUMERIC(10,2) CHECK (median_sale_price_per_sqft >= 0),
    -- ASKING columns
    active_listing_count INTEGER CHECK (active_listing_count >= 0),
    new_listing_count INTEGER CHECK (new_listing_count >= 0),
    median_asking_price NUMERIC(14,2) CHECK (median_asking_price >= 0),
    average_asking_price NUMERIC(14,2) CHECK (average_asking_price >= 0),
    median_asking_price_per_sqft NUMERIC(10,2) CHECK (median_asking_price_per_sqft >= 0),
    median_days_on_market NUMERIC(7,1) CHECK (median_days_on_market >= 0),
    price_reduced_count INTEGER CHECK (price_reduced_count >= 0),
    -- Provenance of the import itself
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    import_job_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_market_evidence_period CHECK (period_start <= period_end),
    -- A SALE row has no listing figures and an ASKING row has no transaction figures
    CONSTRAINT ck_market_evidence_sale_columns CHECK (evidence_type = 'SALE' OR (
        sale_count IS NULL AND median_sale_price IS NULL AND average_sale_price IS NULL
        AND median_sale_price_per_sqft IS NULL)),
    CONSTRAINT ck_market_evidence_asking_columns CHECK (evidence_type = 'ASKING' OR (
        active_listing_count IS NULL AND new_listing_count IS NULL AND median_asking_price IS NULL
        AND average_asking_price IS NULL AND median_asking_price_per_sqft IS NULL
        AND median_days_on_market IS NULL AND price_reduced_count IS NULL)),
    -- Idempotent imports: the same observation from the same source is one row, updated in place
    CONSTRAINT uq_market_evidence_observation UNIQUE (
        source, evidence_type, geography_level, geography_code, period_start, period_end)
);

-- Reading one market over time: every period of a ZIP, county or state, one evidence type
CREATE INDEX idx_market_evidence_geography_period
    ON market_evidence_observations (geography_level, geography_code, evidence_type, period_start);
-- Reading by our own county or state IDs, newest period first
CREATE INDEX idx_market_evidence_county_period
    ON market_evidence_observations (county_id, evidence_type, period_start DESC) WHERE county_id IS NOT NULL;
CREATE INDEX idx_market_evidence_state_period
    ON market_evidence_observations (state_id, evidence_type, period_start DESC) WHERE state_id IS NOT NULL;
