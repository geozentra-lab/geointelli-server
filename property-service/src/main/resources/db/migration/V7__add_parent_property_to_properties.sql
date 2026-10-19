ALTER TABLE properties 
ADD COLUMN parent_property_id BIGINT;

ALTER TABLE properties 
ADD CONSTRAINT fk_property_parent 
FOREIGN KEY (parent_property_id) 
REFERENCES properties(id);