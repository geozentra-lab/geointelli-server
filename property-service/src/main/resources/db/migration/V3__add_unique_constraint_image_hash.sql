ALTER TABLE property_images
ADD CONSTRAINT uk_property_image_hash
UNIQUE(image_hash);