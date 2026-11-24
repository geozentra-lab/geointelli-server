-- SCRUM-127: county FIPS codes left empty by V42 for "Saint" counties stored without a space.
-- V42 (SCRUM-201) matches a county name to its FIPS code after normalising "Saint", "St." and "St" to "st",
-- but only when a space follows ("St. Johns", "Saint Johns"). On RDS the two counties are stored as
-- "SaintJohns" and "SaintLucie", so they kept fips_code NULL and the flood-zone import refuses their files
-- ("county has no fips code"). Same rule as V42 with the space made optional (\s* instead of \s+).
-- Only rows whose fips_code is still NULL are touched, so the file can run on any database, any number of times.
-- Apply by hand in ONE transaction (psql -1 -f ...), like V42; the check at the end rolls back when a
-- Florida county still has no code.

UPDATE counties c
SET fips_code = v.fips
FROM states s,
     (VALUES ('12109', 'St. Johns'), ('12111', 'St. Lucie')) AS v(fips, name)
WHERE s.id = c.state_id
  AND s.code = 'FL'
  AND c.fips_code IS NULL
  AND regexp_replace(regexp_replace(lower(c.name), '^(saint|st\.?)\s*', 'st'), '[^a-z]', '', 'g')
    = regexp_replace(regexp_replace(lower(v.name), '^(saint|st\.?)\s*', 'st'), '[^a-z]', '', 'g');

DO $$
DECLARE
    unmatched TEXT;
BEGIN
    SELECT string_agg(c.name, ', ' ORDER BY c.name) INTO unmatched
    FROM counties c JOIN states s ON s.id = c.state_id
    WHERE s.code = 'FL' AND c.fips_code IS NULL;
    IF unmatched IS NOT NULL THEN
        RAISE EXCEPTION 'SCRUM-127: still no FIPS code for the Florida counties: %', unmatched;
    END IF;
END $$;
