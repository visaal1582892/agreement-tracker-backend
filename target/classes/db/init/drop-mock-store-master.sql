-- Drop the foreign key constraint from agreement_store_mappings
SET @fk_constraint_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agreement_store_mappings'
      AND REFERENCED_TABLE_NAME = 'stores_master'
    LIMIT 1
);

SET @sql = IF(@fk_constraint_name IS NOT NULL, CONCAT('ALTER TABLE agreement_store_mappings DROP FOREIGN KEY ', @fk_constraint_name), 'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop store_id column and related constraints/indexes from agreement_store_mappings if they exist
SET @drop_store_id = IF((SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agreement_store_mappings' AND COLUMN_NAME = 'store_id') > 0,
    'ALTER TABLE agreement_store_mappings DROP COLUMN store_id', 'DO 0');
PREPARE stmt FROM @drop_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the mock master tables
DROP TABLE IF EXISTS stores_master;
DROP TABLE IF EXISTS state_master;

-- Drop unique constraint that includes store_id if it exists
SET @uk_constraint_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agreement_store_mappings'
      AND CONSTRAINT_TYPE = 'UNIQUE'
      AND CONSTRAINT_NAME = 'uk_version_store'
    LIMIT 1
);

SET @sql_uk = IF(@uk_constraint_name IS NOT NULL, 'ALTER TABLE agreement_store_mappings DROP INDEX uk_version_store', 'DO 0');
PREPARE stmt FROM @sql_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
