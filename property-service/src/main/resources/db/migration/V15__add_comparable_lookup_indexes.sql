-- Candidate retrieval reuses existing sales and parcel records.
-- No property identity, folio, sale or ingestion data is changed.
CREATE INDEX IF NOT EXISTS idx_comparable_properties_county_type
    ON properties (county_id, dor_code, id);

CREATE INDEX IF NOT EXISTS idx_comparable_parcels_property
    ON parcels (property_id);

CREATE INDEX IF NOT EXISTS idx_comparable_sales_property_date
    ON sales (property_id, sale_date DESC, id DESC);

-- Detect a recorded deed shared by different properties before using its price.
CREATE INDEX IF NOT EXISTS idx_comparable_sales_instrument_date
    ON sales (BTRIM(instrument_number), sale_date);

CREATE INDEX IF NOT EXISTS idx_comparable_sales_book_page_date
    ON sales (BTRIM(official_record_book), BTRIM(official_record_page), sale_date);