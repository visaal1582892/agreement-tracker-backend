-- Add FY boundaries to agreement_time_periods
ALTER TABLE agreement_time_periods
    ADD COLUMN fy_start_year INT NULL AFTER period_frequency,
    ADD COLUMN fy_end_year INT NULL AFTER fy_start_year;

-- Migrate existing FY data from agreement_jbp_commercial_periods to agreement_time_periods
UPDATE agreement_time_periods atp
INNER JOIN agreement_jbp_commercial_periods jbp ON atp.id = jbp.time_period_id
SET atp.fy_start_year = jbp.fy_start_year,
    atp.fy_end_year = jbp.fy_end_year
WHERE jbp.fy_start_year IS NOT NULL;

-- Ensure indices on the new columns for performance
CREATE INDEX idx_atp_fy_years ON agreement_time_periods (fy_start_year, fy_end_year);

-- Clean up agreement_jbp_commercial_periods
ALTER TABLE agreement_jbp_commercial_periods
    DROP COLUMN calendar_year,
    DROP COLUMN fy_start_year,
    DROP COLUMN fy_end_year;

-- Clean up agreement_jbp_configurations
ALTER TABLE agreement_jbp_configurations
    DROP COLUMN frequency;

-- Drop obsolete tables
DROP TABLE IF EXISTS agreement_jbp_config_periods;
DROP TABLE IF EXISTS agreement_jbp_version_frequencies;
