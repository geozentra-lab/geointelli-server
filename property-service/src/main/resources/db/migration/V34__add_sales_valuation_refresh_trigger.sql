-- Enqueue a valuation refresh when a sale is changed by plain SQL (psql, other services, bulk scripts).
--
-- ValuationRefreshListener already enqueues for writes made through Hibernate. This trigger covers writes
-- that bypass the ORM. Both build the same event key, SALE:<sales.id>:<property_id>:tx:<txid>, in the same
-- transaction, so ON CONFLICT (event_key) keeps a single request when both fire.
--
-- UPDATE OF lists the same sale fields as the listener's INPUTS map; keep the two in sync.
-- When property_id changes, both the old and the new property are enqueued.
-- Large SQL bulk loads create one request per sale; for those, disable the trigger for the load
-- (ALTER TABLE sales DISABLE TRIGGER trg_sales_valuation_refresh) and enqueue a single request instead.

CREATE OR REPLACE FUNCTION enqueue_valuation_refresh_sale() RETURNS trigger AS $$
DECLARE
    v text := 'tx:' || txid_current()::text;
    r record;
BEGIN
    FOR r IN SELECT DISTINCT * FROM (VALUES (NEW.id, NEW.property_id), (OLD.id, OLD.property_id)) t(sale_id, property_id)
             WHERE property_id IS NOT NULL LOOP
        INSERT INTO property_valuation_refresh_requests
            (request_id, event_key, trigger_type, scope_type, property_id, sale_id, source_version)
        VALUES (gen_random_uuid(), 'SALE:' || r.sale_id || ':' || r.property_id || ':' || v,
                'SALE_CHANGE', 'PROPERTY', r.property_id, r.sale_id, v)
        ON CONFLICT (event_key) DO NOTHING;
    END LOOP;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sales_valuation_refresh ON sales;

CREATE TRIGGER trg_sales_valuation_refresh
AFTER INSERT OR DELETE OR UPDATE OF property_id, sale_date, sale_price, qualified_flag, instrument_number,
    qualification_description, sale_type, vacant_flag
ON sales
FOR EACH ROW EXECUTE FUNCTION enqueue_valuation_refresh_sale();
