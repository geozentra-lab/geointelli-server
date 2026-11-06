-- Trigram matching backs address suggestions (the <% operator fails without it) and lets
-- the property search's "address contains" filter use an index.
-- Kept apart from the concurrent index builds, which cannot run inside a transaction.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
