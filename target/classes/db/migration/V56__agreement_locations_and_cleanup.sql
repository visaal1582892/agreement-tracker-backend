-- V56: Introduce normalized agreement_locations table (one row per location per version)
--      and remove the now-redundant flat JSON columns from the agreements table.

-- 1. Create the agreement_locations child table
CREATE TABLE IF NOT EXISTS `agreement_locations` (
  `id`                    BIGINT          NOT NULL AUTO_INCREMENT,
  `created_at`            DATETIME(6)     NOT NULL,
  `created_by_user_id`    BIGINT          DEFAULT NULL,
  `updated_at`            DATETIME(6)     NOT NULL,
  `updated_by_user_id`    BIGINT          DEFAULT NULL,
  `location_type`         VARCHAR(32)     NOT NULL,
  `country_code`          VARCHAR(32)     DEFAULT NULL,
  `country_name`          VARCHAR(128)    DEFAULT NULL,
  `country_sub_name`      VARCHAR(128)    DEFAULT NULL,
  `state_code`            VARCHAR(32)     DEFAULT NULL,
  `state_name`            VARCHAR(128)    DEFAULT NULL,
  `state_sub_name`        VARCHAR(128)    DEFAULT NULL,
  `city_code`             VARCHAR(32)     DEFAULT NULL,
  `city_name`             VARCHAR(128)    DEFAULT NULL,
  `city_sub_name`         VARCHAR(128)    DEFAULT NULL,
  `agreement_version_id`  BIGINT          NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_al_agreement_version_id` (`agreement_version_id`),
  CONSTRAINT `fk_al_agreement_version_id`
      FOREIGN KEY (`agreement_version_id`)
      REFERENCES `agreement_versions` (`id`)
      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Drop the legacy flat JSON location columns from agreements
--    (Data was stored as JSON blobs; the new table supersedes these columns.)
ALTER TABLE `agreements`
    DROP COLUMN IF EXISTS `geography_mode`,
    DROP COLUMN IF EXISTS `partner_states_json`,
    DROP COLUMN IF EXISTS `partner_cities_json`;
