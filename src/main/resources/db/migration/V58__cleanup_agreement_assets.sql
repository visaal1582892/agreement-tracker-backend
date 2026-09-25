-- 1. Add new columns and expand existing enum column sizes to prevent truncation
ALTER TABLE agreement_versions 
  ADD COLUMN asset_category VARCHAR(20),
  ADD COLUMN asset_type VARCHAR(100),
  MODIFY COLUMN commercial_structure VARCHAR(30);

-- 2. Migrate data from agreement_assets to agreement_versions
UPDATE agreement_versions av
JOIN agreement_assets aa ON aa.agreement_version_id = av.id
SET 
  av.asset_category = aa.asset_category,
  av.asset_type = aa.asset_type,
  -- Safely capture whichever payout value was populated
  av.commercial_value = COALESCE(aa.flat_payout, aa.payout_per_store, av.commercial_value),
  av.commercial_structure = IF(aa.flat_payout IS NOT NULL, 'FLAT', 
                            IF(aa.payout_per_store IS NOT NULL, 'PAYOUT_PER_STORE', av.commercial_structure)),
  av.flat_baseline_frequency = IF(aa.flat_payout IS NOT NULL, 'ONE_TIME', av.flat_baseline_frequency),
  av.flat_value_type = IF(aa.flat_payout IS NOT NULL, 'FIXED', av.flat_value_type);

-- 3. Drop the old table
DROP TABLE IF EXISTS agreement_assets;

-- 4. Drop unused columns from agreement_versions
ALTER TABLE agreement_versions
  DROP COLUMN calculation_formula,
  DROP COLUMN product_scope_compute_error;
