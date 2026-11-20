-- PROPOSAL v1.2 (2026-11-18), for property-service / Flyway to apply after proposal v1. Python runs no DDL.
-- The 5-year forecast replaces the 7-year forecast.
--
-- Current table: forecast_5y* replaces forecast_7y*. Every run replaces current rows, so nothing is lost.
-- History table: forecast_5y* is added and forecast_7y* is KEPT. History is append-only and rows published
--   before this change hold real 7-year values; renaming the column would relabel them as 5-year values.
--   Rows published after this change leave forecast_7y* NULL and fill forecast_5y*.
--
-- Order: 1) apply this (additive; the backend keeps reading forecast_7y), 2) one full scoring run (fills
-- forecast_5y on every current row, leaves forecast_7y NULL, becomes the reference for targeted runs),
-- 3) switch the backend to forecast_5y, 4) apply proposed_v1_3_drop_forecast_7y.sql.
-- Python refuses to publish until these columns exist (SchemaMissing); targeted runs wait for step 2.

ALTER TABLE public.property_value_predictions
    ADD COLUMN forecast_5y      NUMERIC(38,2),
    ADD COLUMN forecast_5y_low  NUMERIC(38,2),
    ADD COLUMN forecast_5y_high NUMERIC(38,2);

ALTER TABLE public.property_value_prediction_history
    ADD COLUMN forecast_5y      NUMERIC,
    ADD COLUMN forecast_5y_low  NUMERIC,
    ADD COLUMN forecast_5y_high NUMERIC;
