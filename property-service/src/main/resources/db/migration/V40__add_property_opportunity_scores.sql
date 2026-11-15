-- Stored output of the opportunity scoring service (SCRUM-76): one row per property that has a stored
-- valuation, written by the opportunity score refresh job and read by GET /api/opportunities.
-- A row records the scoring version and the valuation run it was calculated from, so a stale row can be
-- detected and rescored. Properties without a valuation have no row.
CREATE TABLE property_opportunity_scores (
    property_id BIGINT PRIMARY KEY REFERENCES properties(id) ON DELETE CASCADE,
    score_version VARCHAR(32) NOT NULL,
    -- SCORED: the essential evidence exists and score is set; UNAVAILABLE: score is null and reasons explain why
    status VARCHAR(16) NOT NULL CHECK (status IN ('SCORED', 'UNAVAILABLE')),
    score NUMERIC(5,2) CHECK (score BETWEEN 0 AND 100),
    -- Sum of the weights of the factors whose evidence was available
    coverage_percent NUMERIC(5,2) NOT NULL CHECK (coverage_percent BETWEEN 0 AND 100),
    factors JSONB NOT NULL CHECK (jsonb_typeof(factors) = 'array'),
    reasons JSONB NOT NULL CHECK (jsonb_typeof(reasons) = 'array'),
    -- Copied from the property and its valuation so browsing can filter and sort without joins
    county_id BIGINT,
    state_id BIGINT,
    estimated_value NUMERIC(38,2),
    forecast_growth_1y_pct NUMERIC(9,2),
    valuation_run_id VARCHAR(32),
    valuation_model_version VARCHAR(32),
    valuation_data_through DATE,
    scored_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_opportunity_score_status CHECK ((status = 'SCORED') = (score IS NOT NULL))
);

-- Browsing: best score first, optionally within one county or state
CREATE INDEX idx_opportunity_scores_rank
    ON property_opportunity_scores (score_version, score DESC, property_id) WHERE status = 'SCORED';
CREATE INDEX idx_opportunity_scores_county_rank
    ON property_opportunity_scores (county_id, score_version, score DESC, property_id) WHERE status = 'SCORED';
CREATE INDEX idx_opportunity_scores_state_rank
    ON property_opportunity_scores (state_id, score_version, score DESC, property_id) WHERE status = 'SCORED';
-- Estimated value either way, and the estimated-value range filter; estimates are nearly unique, so the few ties
-- are sorted incrementally by score and property ID
CREATE INDEX idx_opportunity_scores_value
    ON property_opportunity_scores (score_version, estimated_value) WHERE status = 'SCORED';
-- Highest one-year growth first, in the full order: many properties share a growth value (80,398 at the top value
-- on the local data), so an index on growth alone made every page sort that whole group (700 ms instead of 1 ms)
CREATE INDEX idx_opportunity_scores_growth
    ON property_opportunity_scores (score_version, forecast_growth_1y_pct DESC, score DESC, property_id)
    WHERE status = 'SCORED';
