-- Year built "Multiple (See Building Info.)" replaced by the first building's year.
-- For a property with several buildings or segments, Miami-Dade answers that text instead of a year, so the
-- property pages showed it, and the year-built filters and sort and the comparables skipped those properties
-- (202,456 of 582,589 properties on the local copy, all with building years). Every one of them gets the actual
-- year of its first building: the latest roll year, then the lowest building number, then the lowest segment.
-- For properties with a single year, the county's year matches that building in 99.88% of cases. Building years
-- outside 1000 to the current year (the county writes 9999 for an unknown year) are skipped; a property without
-- any other building year keeps its text.
-- New imports apply the same rule: the county API ingestion in Java (util/YearBuilt), the buildings CSV import with
-- this statement (CsvImportServiceImpl.FILL_YEAR_BUILT_FROM_BUILDINGS).
-- V52 because V51 is taken by the SCRUM-133 branch (economic indicators). Only rows that still hold the text are
-- touched, so the file can run any number of times. Apply by hand:
--   psql ... -v ON_ERROR_STOP=1 -1 -f V52__fill_year_built_from_buildings.sql
-- Check afterwards (0 expected):
--   SELECT count(*) FROM properties WHERE LOWER(year_built) LIKE '%see building%';

UPDATE properties p
SET year_built = (
        SELECT CAST(b.actual AS VARCHAR)
        FROM buildings b
        WHERE b.property_id = p.id
          AND b.actual BETWEEN 1000 AND EXTRACT(YEAR FROM CURRENT_DATE)
        ORDER BY b.roll_year DESC NULLS LAST, b.building_no ASC NULLS LAST, b.seg_no ASC NULLS LAST, b.id
        LIMIT 1),
    updated_at = LOCALTIMESTAMP
WHERE LOWER(p.year_built) LIKE '%see building%'
  AND EXISTS (
        SELECT 1
        FROM buildings b
        WHERE b.property_id = p.id
          AND b.actual BETWEEN 1000 AND EXTRACT(YEAR FROM CURRENT_DATE));
