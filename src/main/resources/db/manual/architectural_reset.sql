-- Architectural reset: decouple agreements from local company/vendor/product masters.
-- Run manually against agreement_tracker schema. Review on a backup first.

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Truncate transactional / dependent data (children before parents)
TRUNCATE TABLE agreement_action_requests;
TRUNCATE TABLE agreement_approvals;
TRUNCATE TABLE agreement_audits;
TRUNCATE TABLE agreement_reminders;
TRUNCATE TABLE agreement_documents;
TRUNCATE TABLE agreement_computed_products;
TRUNCATE TABLE agreement_product_rules;
TRUNCATE TABLE agreement_division_rules;
TRUNCATE TABLE agreement_manufacturers;
TRUNCATE TABLE agreement_vendors;
TRUNCATE TABLE agreement_slabs;
TRUNCATE TABLE agreement_store_mappings;
TRUNCATE TABLE agreement_asset_payout_periods;
TRUNCATE TABLE agreement_jbp_commercial_periods;
TRUNCATE TABLE agreement_jbp_version_frequencies;
TRUNCATE TABLE agreement_jbp_configurations;
TRUNCATE TABLE agreement_assets;
TRUNCATE TABLE agreement_versions;
TRUNCATE TABLE agreement_states;
TRUNCATE TABLE agreements;
TRUNCATE TABLE company_agreement_groups;

TRUNCATE TABLE consumer_price_off_campaign_products;
TRUNCATE TABLE consumer_price_off_campaigns;

-- 2. Drop join / mock master tables
DROP TABLE IF EXISTS vendor_product_mapping;
DROP TABLE IF EXISTS user_company_assignment;
DROP TABLE IF EXISTS product_master;
DROP TABLE IF EXISTS division_master;
DROP TABLE IF EXISTS manufacturer_master;
DROP TABLE IF EXISTS vendor_master;
DROP TABLE IF EXISTS company_master;

-- 3. Recreate agreement group table without company FK (table name unchanged)
CREATE TABLE IF NOT EXISTS company_agreement_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    is_active BIT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    created_by_user_id BIGINT NULL,
    updated_at DATETIME(6) NULL,
    updated_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cag_name (name),
    KEY idx_cag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Agreement versions: invoice vendor snapshot (no vendor_master FK)
ALTER TABLE agreement_versions
    DROP FOREIGN KEY IF EXISTS fk_av_invoice_vendor;

ALTER TABLE agreement_versions
    ADD COLUMN IF NOT EXISTS invoice_vendor_name_snapshot VARCHAR(255) NULL AFTER invoice_vendor_id;

SET FOREIGN_KEY_CHECKS = 1;
