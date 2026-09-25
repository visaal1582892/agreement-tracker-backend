-- Drop the unique constraint
ALTER TABLE agreement_store_mappings DROP INDEX uk_version_store_code;

-- Alter table to drop store_code and add new columns
ALTER TABLE agreement_store_mappings
    DROP COLUMN store_code,
    ADD COLUMN store_id VARCHAR(50) NOT NULL,
    ADD COLUMN name VARCHAR(255),
    ADD COLUMN address TEXT,
    ADD COLUMN pin_code INT,
    ADD COLUMN region_1 VARCHAR(100),
    ADD COLUMN region_2 VARCHAR(100),
    ADD COLUMN region_3 VARCHAR(100),
    ADD COLUMN is_custom BOOLEAN NOT NULL DEFAULT FALSE;

-- Add new unique constraint, assuming store_id is unique per agreement_version
ALTER TABLE agreement_store_mappings ADD CONSTRAINT uk_version_store_id UNIQUE (agreement_version_id, store_id);
