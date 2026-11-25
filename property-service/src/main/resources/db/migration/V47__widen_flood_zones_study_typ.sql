-- FEMA's STUDY_TYP is up to 28 characters ("SFHAs WITH MEDIUM FLOOD RISK"), but V42 made flood_zones.study_typ
-- VARCHAR(20), so loading the flood zones of 46 of the 67 Florida counties failed with "value too long for type
-- character varying(20)". Every other flood_zones text column fits the longest value in the 67 FEMA files.
--
-- Widening a VARCHAR changes only the column definition: no row is rewritten and no index is rebuilt. The ALTER
-- still waits for every open transaction that reads flood_zones, so apply it with a lock timeout:
--   PGOPTIONS='-c lock_timeout=10s' psql ... -v ON_ERROR_STOP=1 -1 -f V47__widen_flood_zones_study_typ.sql
-- Running it again changes nothing.
ALTER TABLE flood_zones ALTER COLUMN study_typ TYPE VARCHAR(50);
