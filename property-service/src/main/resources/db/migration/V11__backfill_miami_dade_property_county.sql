INSERT INTO states (code, name)
VALUES ('FL', 'Florida')
ON CONFLICT (code) DO NOTHING;

INSERT INTO counties (state_id, name)
SELECT id, 'Miami-Dade'
FROM states
WHERE code = 'FL'
ON CONFLICT (state_id, LOWER(name)) DO NOTHING;

UPDATE properties p
SET county_id = c.id,
    updated_at = CURRENT_TIMESTAMP
FROM counties c
JOIN states s ON s.id = c.state_id
WHERE s.code = 'FL'
  AND LOWER(c.name) = 'miami-dade'
  AND p.county_id IS NULL;

-- Parcels inherit county/state through their existing property_id relationship.
