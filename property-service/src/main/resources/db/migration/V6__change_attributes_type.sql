ALTER TABLE properties 
    ALTER COLUMN bathroom_count TYPE NUMERIC(5, 1) USING bathroom_count::NUMERIC(5, 1),
    ALTER COLUMN bedroom_count TYPE NUMERIC USING bedroom_count::NUMERIC,
    ALTER COLUMN building_actual_area TYPE NUMERIC USING building_actual_area::NUMERIC,
    ALTER COLUMN building_base_area TYPE NUMERIC USING building_base_area::NUMERIC,
    ALTER COLUMN building_effective_area TYPE NUMERIC USING building_effective_area::NUMERIC,
    ALTER COLUMN building_gross_area TYPE NUMERIC USING building_gross_area::NUMERIC,
    ALTER COLUMN building_heated_area TYPE NUMERIC USING building_heated_area::NUMERIC,
    ALTER COLUMN neighborhood TYPE character varying(255),
    ALTER COLUMN floor_count TYPE NUMERIC USING floor_count::NUMERIC;

ALTER TABLE buildings ALTER heated_area TYPE NUMERIC USING heated_area::NUMERIC;