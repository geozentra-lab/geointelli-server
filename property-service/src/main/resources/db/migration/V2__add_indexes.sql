CREATE INDEX IF NOT EXISTS idx_address_zip_address ON address(zip, address);

CREATE INDEX IF NOT EXISTS idx_property_image_hash ON property_image(property_id, image_hash);