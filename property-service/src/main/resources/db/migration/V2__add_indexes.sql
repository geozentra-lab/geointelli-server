CREATE INDEX idx_addresses_address
ON addresses(LOWER(address));

CREATE INDEX idx_addresses_lower_address
ON addresses(address);

CREATE INDEX idx_addresses_property_id
ON addresses(property_id);

CREATE INDEX idx_images_property_id
ON property_images(property_id);