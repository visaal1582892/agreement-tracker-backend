-- Drop agreement-relative FY clutter from shared time periods.
-- Absolute months remain in agreement_time_period_months.
-- FY context lives on agreement_versions.financial_year_start_month.

ALTER TABLE agreement_time_periods DROP INDEX uk_atp_name;

ALTER TABLE agreement_time_periods
    DROP COLUMN calendar_year,
    DROP COLUMN fy_start_year,
    DROP COLUMN fy_end_year;

CREATE INDEX idx_atp_name ON agreement_time_periods (name);
