-- Listing source and field mapping (SCRUM-118): the machine-readable copy of
-- docs/listing-source-field-mapping.md. One row per authorized listing provider and one row per provider
-- field, saying which column of the future listings table (SCRUM-119) it maps to, or why it is not mapped.
-- No listing data is stored here; the import (SCRUM-119) and the matching (SCRUM-120) read this mapping.
CREATE TABLE listing_sources (
    id BIGSERIAL PRIMARY KEY,
    -- Provider code, upper-case, used by the listings table as source_code
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    provider VARCHAR(160) NOT NULL,
    -- How the data is read (library, API, file), including access constraints
    access_method TEXT NOT NULL,
    -- PENDING: proposed, awaiting the product owner; AUTHORIZED: may feed the listings table; REVOKED: no longer allowed
    authorization_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (authorization_status IN ('PENDING', 'AUTHORIZED', 'REVOKED')),
    authorized_at DATE,
    licence_note TEXT,
    -- Markets covered and how they were collected
    coverage TEXT NOT NULL,
    -- BATCH_PULL: we re-run the collection and re-import; FEED: the provider pushes changes
    refresh_method VARCHAR(16) NOT NULL CHECK (refresh_method IN ('BATCH_PULL', 'FEED')),
    refresh_cadence VARCHAR(120),
    -- Provider fields that identify a listing across imports (never a row number)
    identity_fields TEXT NOT NULL,
    known_limits TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_listing_sources_code UNIQUE (code)
);

CREATE TABLE listing_source_fields (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES listing_sources(id) ON DELETE CASCADE,
    -- Group the ticket asks for: identifiers, timestamps, status, asking price, sale price, location, description
    field_group VARCHAR(16) NOT NULL CHECK (field_group IN (
        'IDENTIFIER', 'TIMESTAMP', 'STATUS', 'ASKING_PRICE', 'SALE_PRICE', 'LOCATION', 'DESCRIPTION', 'NOT_MAPPED')),
    -- Provider field, dotted path for nested values (location.address.city)
    source_field VARCHAR(160) NOT NULL,
    -- Column of the listings table; NULL when the field is not imported
    target_column VARCHAR(64),
    data_type VARCHAR(40),
    -- AVAILABLE: (nearly) always filled; PARTIAL: filled on some records; UNAVAILABLE: provider does not give it;
    -- EXCLUDED: provider gives it, we do not import it (by decision)
    availability VARCHAR(16) NOT NULL CHECK (availability IN ('AVAILABLE', 'PARTIAL', 'UNAVAILABLE', 'EXCLUDED')),
    note TEXT,
    -- Order of the rows as in the document
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_listing_source_fields UNIQUE (source_id, source_field),
    -- A mapped field names its column; an unavailable or excluded one has none
    CONSTRAINT ck_listing_source_fields_target CHECK (
        (availability IN ('AVAILABLE', 'PARTIAL') AND target_column IS NOT NULL)
        OR (availability IN ('UNAVAILABLE', 'EXCLUDED') AND target_column IS NULL))
);

CREATE INDEX idx_listing_source_fields_source_group ON listing_source_fields (source_id, field_group);

-- Realtor.com through HomeHarvest: the feed the team already uses for the Florida photo backfill.
INSERT INTO listing_sources (code, name, provider, access_method, authorization_status, authorized_at, licence_note,
                             coverage, refresh_method, refresh_cadence, identity_fields, known_limits)
VALUES ('REALTOR_COM',
        'Realtor.com listing search',
        'Move, Inc. (Realtor.com), listings syndicated from the local MLS boards',
        'Realtor.com internal GraphQL search read through the HomeHarvest library (scrape_property, return_type=raw) per ZIP code and listing type (sold, for_sale, for_rent, pending, off_market). No API key, no contract. US IP required (HTTP 403 elsewhere); about one ZIP per minute.',
        'PENDING',
        NULL,
        'Realtor.com Terms of Use; MLS data carries the listing broker''s copyright. Store listing facts only, not the description text or photos, unless the authorization covers them.',
        'Florida: all 67 counties by ZIP code (925 ZIPs with homes in our database, collected 2026-11-18 to 2026-11-20). Arizona: Maricopa County by ZIP code. Collected to files; not imported.',
        'BATCH_PULL',
        'weekly per supported county (proposed)',
        'property_id (stable home id) + listing_id (one per listing event); MLS number in source.listing_id, MLS board in source.id. Never a file row number.',
        '10,000 results per query (collector splits by year, then month); search history from about 2016; each record is a snapshot of the current status, no status history; rate limited.');

INSERT INTO listing_source_fields (source_id, field_group, source_field, target_column, data_type, availability, note, display_order)
SELECT s.id, f.field_group, f.source_field, f.target_column, f.data_type, f.availability, f.note, f.display_order
FROM listing_sources s,
     (VALUES
        -- Identifiers
        ('IDENTIFIER', 'property_id', 'source_property_id', 'text', 'AVAILABLE',
         'Realtor home id, stable across listings of the same home. Matching key for SCRUM-120 together with the address.', 10),
        ('IDENTIFIER', 'listing_id', 'source_listing_id', 'text', 'PARTIAL',
         'One per listing event; changes when the home is relisted. Null when the home was never listed (tax-record-only off_market homes).', 20),
        ('IDENTIFIER', 'source.listing_id', 'mls_number', 'text', 'PARTIAL',
         'MLS number. Returned by the provider but dropped by the current collector (DROP list in scrape_realtor_zips.py); SCRUM-119 must keep it.', 30),
        ('IDENTIFIER', 'source.id', 'mls_set', 'text', 'PARTIAL',
         'MLS board code. Same remark as source.listing_id.', 40),
        ('IDENTIFIER', 'permalink', 'source_permalink', 'text', 'AVAILABLE',
         'Path of the Realtor page, for provenance. href is the full URL and is derived from it.', 50),
        ('IDENTIFIER', 'tax_record.public_record_id', 'source_public_record_id', 'text', 'AVAILABLE',
         'Provider public-record key; tax_record.cl_id carries the same value.', 60),
        ('IDENTIFIER', 'tax_record.tax_parcel_id', 'source_tax_parcel_id', 'text', 'AVAILABLE',
         'Provider parcel key (UUID-like), not the county folio.', 70),
        ('IDENTIFIER', 'tax_record.apn', NULL, 'text', 'UNAVAILABLE',
         'Assessor parcel number. Empty in every Florida record seen, so not mapped (would be source_apn). Folio-like when present, but a folio alone is not unique across counties, so never used on its own to attach a listing.', 80),
        -- Timestamps
        ('TIMESTAMP', 'list_date', 'listed_at', 'timestamp (UTC)', 'PARTIAL',
         'When the listing entered the provider system. Filled on listed records only.', 110),
        ('TIMESTAMP', 'pending_date', 'pending_at', 'timestamp (UTC)', 'PARTIAL',
         'When the listing went pending.', 120),
        ('TIMESTAMP', 'last_status_change_date', 'status_changed_at', 'timestamp (UTC)', 'PARTIAL',
         'Last status change.', 130),
        ('TIMESTAMP', 'last_update_date', 'source_updated_at', 'timestamp (UTC)', 'AVAILABLE',
         'Last change of the record at the provider; the freshness field.', 140),
        ('TIMESTAMP', 'tax_record.last_update_date', 'tax_record_updated_at', 'date', 'AVAILABLE',
         'When the provider refreshed the public record.', 150),
        ('TIMESTAMP', '(collection time)', 'collected_at', 'timestamp (UTC)', 'AVAILABLE',
         'Not a provider field: when the collector fetched the record. Written by the import from the file date.', 160),
        -- Status
        ('STATUS', 'status', 'listing_status', 'enum', 'AVAILABLE',
         'Provider values for_sale, pending, sold, off_market, for_rent, other; stored as FOR_SALE, PENDING, SOLD, OFF_MARKET, FOR_RENT, OTHER. off_market also covers homes never listed.', 210),
        ('STATUS', 'mls_status', 'mls_status', 'text', 'PARTIAL',
         'MLS wording (Active, Sold, Expired, Pending, ...). Kept as given, not normalised.', 220),
        ('STATUS', 'flags.is_pending', 'flag_pending', 'boolean', 'PARTIAL',
         'Null means unknown, not false.', 230),
        ('STATUS', 'flags.is_contingent', 'flag_contingent', 'boolean', 'PARTIAL',
         'Null means unknown, not false.', 240),
        ('STATUS', 'flags.is_new_construction', 'flag_new_construction', 'boolean', 'PARTIAL',
         'Null means unknown, not false.', 250),
        ('STATUS', 'tags', 'tags', 'text[]', 'PARTIAL',
         'Provider feature tags (garage_2_or_more, swimming_pool, ...). Informational. Any financing-related tag is dropped at import.', 260),
        -- Asking price: never mixed with the sale price
        ('ASKING_PRICE', 'list_price', 'asking_price', 'numeric(14,2)', 'PARTIAL',
         'Current asking price in USD. Filled on listed records only.', 310),
        ('ASKING_PRICE', 'list_price_min', 'asking_price_min', 'numeric(14,2)', 'PARTIAL',
         'Low end of a price range (new-construction communities, rentals). Empty in every Florida sale record seen.', 320),
        ('ASKING_PRICE', 'list_price_max', 'asking_price_max', 'numeric(14,2)', 'PARTIAL',
         'High end of a price range. Same remark.', 330),
        ('ASKING_PRICE', 'price_per_sqft', 'asking_price_per_sqft', 'numeric(10,2)', 'PARTIAL',
         'Provider price per square foot. On sold records it is derived from the sold price, so the import stores it only when status is for_sale or pending.', 340),
        ('ASKING_PRICE', 'hoa.fee', 'hoa_fee_monthly', 'numeric(10,2)', 'PARTIAL',
         'Association fee per month; 0 means none. A fee, not a financing figure.', 350),
        -- Sale price: completed sales, kept apart
        ('SALE_PRICE', 'last_sold_price', 'last_sold_price', 'numeric(14,2)', 'PARTIAL',
         'Price of the last completed sale as the provider knows it. Own column, never asking_price. The county sales table stays authoritative; this is a cross-check.', 410),
        ('SALE_PRICE', 'last_sold_date', 'last_sold_at', 'date', 'PARTIAL',
         'Date of that sale.', 420),
        -- Address and location, for matching in SCRUM-120
        ('LOCATION', 'location.address.line', 'address_line', 'text', 'AVAILABLE',
         'Street line as the provider formats it.', 510),
        ('LOCATION', 'location.address.street_number', 'street_number', 'text', 'AVAILABLE', NULL, 520),
        ('LOCATION', 'location.address.street_direction', 'street_direction', 'text', 'PARTIAL',
         'Null when the street has no direction.', 530),
        ('LOCATION', 'location.address.street_name', 'street_name', 'text', 'AVAILABLE', NULL, 540),
        ('LOCATION', 'location.address.street_suffix', 'street_suffix', 'text', 'AVAILABLE',
         'Rd, Ave, Ct, ...; null on a few records.', 550),
        ('LOCATION', 'location.address.unit', 'unit', 'text', 'PARTIAL',
         'Condo or apartment unit. Null on houses.', 560),
        ('LOCATION', 'location.address.city', 'city', 'text', 'AVAILABLE', NULL, 570),
        ('LOCATION', 'location.address.state_code', 'state_code', 'text', 'AVAILABLE', 'Two letters.', 580),
        ('LOCATION', 'location.address.postal_code', 'zip_code', 'text', 'AVAILABLE', 'Five digits.', 590),
        ('LOCATION', 'location.address.coordinate.lat', 'latitude', 'numeric(9,6)', 'AVAILABLE', 'WGS84.', 600),
        ('LOCATION', 'location.address.coordinate.lon', 'longitude', 'numeric(9,6)', 'AVAILABLE', 'WGS84.', 610),
        ('LOCATION', 'location.county.name', 'county_name', 'text', 'AVAILABLE', NULL, 620),
        ('LOCATION', 'location.county.fips_code', 'county_fips', 'text', 'AVAILABLE',
         'Five-digit FIPS; same code as the SCRUM-131 COUNTY geography.', 630),
        ('LOCATION', 'location.neighborhoods[].name', 'neighborhood_name', 'text', 'PARTIAL', 'Rare.', 640),
        -- Home description: secondary, the appraiser record stays authoritative
        ('DESCRIPTION', 'description.type', 'property_type', 'text', 'AVAILABLE',
         'single_family, condos, townhomes, mobile, land, ...', 710),
        ('DESCRIPTION', 'description.beds', 'bedrooms', 'integer', 'PARTIAL',
         'Filled when the listing says so; the field the photo backfill uses for bedrooms.', 720),
        ('DESCRIPTION', 'description.baths_full', 'bathrooms_full', 'integer', 'PARTIAL', NULL, 730),
        ('DESCRIPTION', 'description.baths_half', 'bathrooms_half', 'integer', 'PARTIAL', NULL, 740),
        ('DESCRIPTION', 'description.sqft', 'living_sqft', 'integer', 'PARTIAL', NULL, 750),
        ('DESCRIPTION', 'description.lot_sqft', 'lot_sqft', 'integer', 'PARTIAL', NULL, 760),
        ('DESCRIPTION', 'description.year_built', 'year_built', 'integer', 'PARTIAL', NULL, 770),
        ('DESCRIPTION', 'description.stories', 'stories', 'integer', 'PARTIAL', NULL, 780),
        ('DESCRIPTION', 'description.garage', 'garage_spaces', 'integer', 'PARTIAL', NULL, 790),
        ('DESCRIPTION', 'description.text', NULL, 'text', 'EXCLUDED',
         'Broker marketing text, copyright; not stored.', 800),
        ('DESCRIPTION', 'primary_photo, photos[]', NULL, 'url', 'EXCLUDED',
         'Photos are handled by the image pipeline (property_images), not by the listings table.', 810),
        -- Not mapped
        ('NOT_MAPPED', 'estimates.currentValues[], current_estimates[]', NULL, NULL, 'EXCLUDED',
         'Third-party automated valuations (Quantarium, CoreLogic, Collateral). Estimates, not observations; the valuation module has its own sources.', 910),
        ('NOT_MAPPED', 'taxHistory[], nearbySchools', NULL, NULL, 'EXCLUDED',
         'Assessments and taxes come from the county, schools from SCRUM-125.', 920),
        ('NOT_MAPPED', 'advertisers', NULL, NULL, 'EXCLUDED',
         'Agent, broker and office contact data. Personal data; the collector already drops it.', 930),
        ('NOT_MAPPED', 'open_houses, pet_policy, units, monthly_fees, one_time_fees, parking, popularity, terms', NULL, NULL, 'EXCLUDED',
         'Rental or marketing details; the collector drops most of them.', 940),
        ('NOT_MAPPED', '(mortgage, financing, monthly payment, rate fields; flags.is_foreclosure)', NULL, NULL, 'EXCLUDED',
         'Not requested by the collector query and not imported by policy: the platform stores no financing data.', 950),
        ('NOT_MAPPED', '(owner names, deed book and page, instrument numbers)', NULL, NULL, 'UNAVAILABLE',
         'Not published by the provider; they stay in the county sales and owners tables.', 960),
        ('NOT_MAPPED', '(listing history: previous prices and statuses)', NULL, NULL, 'UNAVAILABLE',
         'Each record is the current snapshot; history accumulates only through repeated imports.', 970)
     ) AS f(field_group, source_field, target_column, data_type, availability, note, display_order)
WHERE s.code = 'REALTOR_COM';
