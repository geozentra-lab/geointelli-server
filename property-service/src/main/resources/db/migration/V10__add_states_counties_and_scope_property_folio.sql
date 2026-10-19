CREATE TABLE states (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(2) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_states_code UNIQUE (code),
    CONSTRAINT ck_states_code CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_states_name CHECK (name = BTRIM(name) AND name <> '')
);

CREATE UNIQUE INDEX uq_states_name ON states (LOWER(name));

CREATE TABLE counties (
    id BIGSERIAL PRIMARY KEY,
    state_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_counties_state FOREIGN KEY (state_id) REFERENCES states(id),
    CONSTRAINT ck_counties_name CHECK (name = BTRIM(name) AND name <> '')
);

CREATE UNIQUE INDEX uq_counties_state_name ON counties (state_id, LOWER(name));

ALTER TABLE properties ADD COLUMN county_id BIGINT;
ALTER TABLE properties ADD CONSTRAINT fk_properties_county
    FOREIGN KEY (county_id) REFERENCES counties(id);

ALTER TABLE properties ADD CONSTRAINT uq_properties_county_folio
    UNIQUE (county_id, folio);

CREATE UNIQUE INDEX uq_properties_unassigned_folio
    ON properties (folio) WHERE county_id IS NULL;

DO $$
DECLARE
    folio_column SMALLINT;
    old_unique RECORD;
BEGIN
    SELECT attnum INTO STRICT folio_column
    FROM pg_attribute
    WHERE attrelid = 'properties'::regclass AND attname = 'folio' AND NOT attisdropped;

    FOR old_unique IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'properties'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[folio_column]
    LOOP
        EXECUTE format('ALTER TABLE properties DROP CONSTRAINT %I', old_unique.conname);
    END LOOP;

    FOR old_unique IN
        SELECT indexrelid::regclass AS index_name
        FROM pg_index
        WHERE indrelid = 'properties'::regclass
          AND indisunique AND NOT indisprimary
          AND indnkeyatts = 1 AND indkey[0] = folio_column
          AND indpred IS NULL AND indexprs IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM pg_constraint WHERE conindid = indexrelid
          )
    LOOP
        EXECUTE format('DROP INDEX %s', old_unique.index_name);
    END LOOP;
END $$;
