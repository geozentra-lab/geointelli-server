-- Indexes for GET /api/addresses/search. Built concurrently so ingestion keeps writing. A failed
-- concurrent build leaves an INVALID index that IF NOT EXISTS skips: drop it and re-run.

-- By raw address, matched ignoring case, commas, dots and colons. The expression must stay
-- identical to AddressRepository.findPropertyIdsByRawAddress.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_address_search_trgm
    ON addresses USING gin (LOWER(REPLACE(REPLACE(REPLACE(address, ',', ''), '.', ''), ':', '')) gin_trgm_ops);

-- GET /api/addresses/search by fields: exact street number, and substring matches on street name and city.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_street_number
    ON addresses (street_number);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_lower_street_name_trgm
    ON addresses USING gin (LOWER(street_name) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_addresses_lower_city_trgm
    ON addresses USING gin (LOWER(city) gin_trgm_ops);
