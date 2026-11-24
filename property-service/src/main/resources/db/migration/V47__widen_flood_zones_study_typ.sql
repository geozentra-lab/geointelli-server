-- SCRUM-127: flood_zones.study_typ (V42, SCRUM-201) is VARCHAR(20) but FEMA's STUDY_TYP values are up to
-- 28 characters ("SFHAs WITH MEDIUM FLOOD RISK", "SFHA with BFE and floodway"). The flood-zone import of
-- 38 of the 67 Florida county files failed on RDS with "value too long for type character varying(20)".
-- Measured over all 67 files (2026-11-24): longest STUDY_TYP 28; every other text column fits its V42 size.
-- Widening a varchar only changes the type's limit; existing rows are untouched. Safe to rerun.

ALTER TABLE flood_zones ALTER COLUMN study_typ TYPE VARCHAR(50);
