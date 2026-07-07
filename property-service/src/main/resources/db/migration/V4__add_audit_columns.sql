ALTER TABLE addresses ADD COLUMN created_at TIMESTAMP;
ALTER TABLE addresses ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE assessments ADD COLUMN created_at TIMESTAMP;
ALTER TABLE assessments ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE buildings ADD COLUMN created_at TIMESTAMP;
ALTER TABLE buildings ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE extra_features ADD COLUMN created_at TIMESTAMP;
ALTER TABLE extra_features ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE lands ADD COLUMN created_at TIMESTAMP;
ALTER TABLE lands ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE owners ADD COLUMN created_at TIMESTAMP;
ALTER TABLE owners ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE parcels ADD COLUMN created_at TIMESTAMP;
ALTER TABLE parcels ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE properties ADD COLUMN created_at TIMESTAMP;
ALTER TABLE properties ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE property_images ADD COLUMN created_at TIMESTAMP;
ALTER TABLE property_images ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE property_owner ADD COLUMN created_at TIMESTAMP;
ALTER TABLE property_owner ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE property_value_predictions ADD COLUMN created_at TIMESTAMP;
ALTER TABLE property_value_predictions ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE sales ADD COLUMN created_at TIMESTAMP;
ALTER TABLE sales ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE taxes ADD COLUMN created_at TIMESTAMP;
ALTER TABLE taxes ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE users ADD COLUMN created_at TIMESTAMP;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP;

UPDATE addresses SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE assessments SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE buildings SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE extra_features SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE lands SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE owners SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE parcels SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE properties SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE property_images SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE property_owner SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE property_value_predictions SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE sales SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE taxes SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;
UPDATE users SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;


ALTER TABLE addresses ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE addresses ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE assessments ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE assessments ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE buildings ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE buildings ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE extra_features ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE extra_features ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE lands ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE lands ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE owners ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE owners ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE parcels ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE parcels ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE properties ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE properties ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE property_images ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE property_images ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE property_owner ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE property_owner ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE property_value_predictions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE property_value_predictions ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE sales ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE sales ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE taxes ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE taxes ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;