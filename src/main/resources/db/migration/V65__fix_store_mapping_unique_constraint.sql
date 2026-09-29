-- 1. Clear existing mapped data so we can safely add a NOT NULL column
DELETE FROM agreement_store_mappings;

-- 2. Add the missing store_id column
ALTER TABLE agreement_store_mappings ADD COLUMN store_id varchar(50) NOT NULL;

-- 3. Drop the bad constraint that only locks the version ID
ALTER TABLE agreement_store_mappings DROP INDEX uk_version_store_id;

-- 4. Add the correct composite constraint allowing multiple unique stores per version
ALTER TABLE agreement_store_mappings ADD CONSTRAINT uk_version_store_id UNIQUE (agreement_version_id, store_id);