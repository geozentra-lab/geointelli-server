-- Columns written by geointelli-models (src/pipeline/score_all.py --write): v17 current value with
-- 80%/90% ranges and confidence label, v18 1/3/7-year forecasts, run metadata. Idempotent; the
-- scorer applies the same statements (sql/property_value_predictions_schema.sql) before loading.
ALTER TABLE property_value_predictions ALTER COLUMN model_version SET DATA TYPE VARCHAR(32);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS predicted_price_low_90 NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS predicted_price_high_90 NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS confidence VARCHAR(10);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS has_sale_history BOOLEAN;
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_1y NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_1y_low NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_1y_high NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS unchanged_home_value_1y NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_3y NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_3y_low NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_3y_high NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_7y NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_7y_low NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS forecast_7y_high NUMERIC(38,2);
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS market_outlook_1y_pct DOUBLE PRECISION;
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS data_through DATE;
ALTER TABLE property_value_predictions ADD COLUMN IF NOT EXISTS notes TEXT;
