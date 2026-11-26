-- Admin user search (GET /api/admin/users/search, UserRepository.searchByPrefix): "starts with" on the lowercase
-- email, full name ("first last") and last name, and the check that a row is the newest one of its email.
--
-- The three columns are computed by Postgres on every insert and update; the application never writes them (the
-- entity does not map them). Collation "C" sorts by code point, so the values starting with a prefix form one range
-- of the index (prefix <= value < prefix with its last character increased), which the query uses instead of LIKE.
-- Unlike LIKE with a bound parameter, a range keeps using the index when Postgres switches the prepared statement
-- to a generic plan, and it needs no escaping of % or _.
--
-- Adding stored columns rewrites the users table once, under a lock that also blocks reads; users is small.
-- IF NOT EXISTS: safe to apply again.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_search text COLLATE "C"
        GENERATED ALWAYS AS (lower(email)) STORED,
    ADD COLUMN IF NOT EXISTS full_name_search text COLLATE "C"
        GENERATED ALWAYS AS (lower(coalesce(first_name, '') || ' ' || coalesce(last_name, ''))) STORED,
    ADD COLUMN IF NOT EXISTS last_name_search text COLLATE "C"
        GENERATED ALWAYS AS (lower(last_name)) STORED;

CREATE INDEX IF NOT EXISTS idx_users_email_search ON users (email_search);
CREATE INDEX IF NOT EXISTS idx_users_full_name_search ON users (full_name_search);
CREATE INDEX IF NOT EXISTS idx_users_last_name_search ON users (last_name_search);

ANALYZE users;
