ALTER TABLE agreement_time_periods DROP INDEX uk_atp_name;
ALTER TABLE agreement_time_periods DROP COLUMN calendar_year;
ALTER TABLE agreement_time_periods DROP COLUMN fy_start_year;
ALTER TABLE agreement_time_periods DROP COLUMN fy_end_year;
