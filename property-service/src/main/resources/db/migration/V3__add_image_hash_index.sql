CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_property_images_image_hash
ON property_images (image_hash);