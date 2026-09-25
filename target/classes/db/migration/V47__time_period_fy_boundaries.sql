ALTER TABLE agreement_time_periods
    ADD COLUMN calendar_year INT NULL AFTER period_frequency,
    ADD COLUMN fy_start_year INT NULL AFTER calendar_year,
    ADD COLUMN fy_end_year INT NULL AFTER fy_start_year;

ALTER TABLE agreement_jbp_commercial_periods
    ADD COLUMN calendar_year INT NULL AFTER parent_time_period_id,
    ADD COLUMN fy_start_year INT NULL AFTER calendar_year,
    ADD COLUMN fy_end_year INT NULL AFTER fy_start_year;

CREATE INDEX idx_atp_calendar_year ON agreement_time_periods (calendar_year);
CREATE INDEX idx_atp_fy_years ON agreement_time_periods (fy_start_year, fy_end_year);
CREATE INDEX idx_jbp_cp_fy_years ON agreement_jbp_commercial_periods (fy_start_year, fy_end_year);

UPDATE agreement_time_periods
SET calendar_year = CASE
    WHEN name REGEXP '^[0-9]{2}(-[0-9]{2})+ \\([0-9]{4}\\)$'
        THEN CAST(TRIM(TRAILING ')' FROM SUBSTRING_INDEX(name, '(', -1)) AS UNSIGNED)
    WHEN name REGEXP '^[0-9]{2}-[0-9]{4}$'
        THEN CAST(SUBSTRING_INDEX(name, '-', -1) AS UNSIGNED)
    WHEN name REGEXP '^FY [0-9]{4}-[0-9]{4}'
        THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(name, ' ', 2), ' ', -1) AS UNSIGNED)
    ELSE NULL
END
WHERE calendar_year IS NULL;

UPDATE agreement_time_periods
SET fy_start_year = CASE
    WHEN name REGEXP '^[0-9]{2}(-[0-9]{2})+ \\([0-9]{4}\\)$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) >= 4
        THEN CAST(TRIM(TRAILING ')' FROM SUBSTRING_INDEX(name, '(', -1)) AS UNSIGNED)
    WHEN name REGEXP '^[0-9]{2}(-[0-9]{2})+ \\([0-9]{4}\\)$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) < 4
        THEN CAST(TRIM(TRAILING ')' FROM SUBSTRING_INDEX(name, '(', -1)) AS UNSIGNED) - 1
    WHEN name REGEXP '^[0-9]{2}-[0-9]{4}$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) >= 4
        THEN CAST(SUBSTRING_INDEX(name, '-', -1) AS UNSIGNED)
    WHEN name REGEXP '^[0-9]{2}-[0-9]{4}$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) < 4
        THEN CAST(SUBSTRING_INDEX(name, '-', -1) AS UNSIGNED) - 1
    WHEN name REGEXP '^FY [0-9]{4}-[0-9]{4}'
        THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(name, ' ', 2), ' ', -1) AS UNSIGNED)
    ELSE NULL
END,
fy_end_year = CASE
    WHEN name REGEXP '^[0-9]{2}(-[0-9]{2})+ \\([0-9]{4}\\)$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) >= 4
        THEN CAST(TRIM(TRAILING ')' FROM SUBSTRING_INDEX(name, '(', -1)) AS UNSIGNED) + 1
    WHEN name REGEXP '^[0-9]{2}(-[0-9]{2})+ \\([0-9]{4}\\)$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) < 4
        THEN CAST(TRIM(TRAILING ')' FROM SUBSTRING_INDEX(name, '(', -1)) AS UNSIGNED)
    WHEN name REGEXP '^[0-9]{2}-[0-9]{4}$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) >= 4
        THEN CAST(SUBSTRING_INDEX(name, '-', -1) AS UNSIGNED) + 1
    WHEN name REGEXP '^[0-9]{2}-[0-9]{4}$' AND CAST(SUBSTRING(name, 1, 2) AS UNSIGNED) < 4
        THEN CAST(SUBSTRING_INDEX(name, '-', -1) AS UNSIGNED)
    WHEN name REGEXP '^FY [0-9]{4}-[0-9]{4}'
        THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(name, ' ', 3), ' ', -1) AS UNSIGNED)
    ELSE NULL
END
WHERE fy_start_year IS NULL;

UPDATE agreement_jbp_commercial_periods jbp
    INNER JOIN agreement_time_periods atp ON atp.id = jbp.time_period_id
SET jbp.calendar_year = atp.calendar_year,
    jbp.fy_start_year = atp.fy_start_year,
    jbp.fy_end_year = atp.fy_end_year
WHERE jbp.calendar_year IS NULL;
