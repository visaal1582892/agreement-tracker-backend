-- Drop deprecated agreement_assets table and unused columns from agreement_versions
DROP TABLE IF EXISTS agreement_assets;

ALTER TABLE agreement_versions DROP COLUMN calculation_formula;
ALTER TABLE agreement_versions DROP COLUMN product_scope_compute_error;
