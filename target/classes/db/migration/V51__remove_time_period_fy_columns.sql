-- Drop the redundant FY columns from agreement_time_periods

ALTER TABLE agreement_time_periods
    DROP COLUMN fy_start_year,
    DROP COLUMN fy_end_year;
