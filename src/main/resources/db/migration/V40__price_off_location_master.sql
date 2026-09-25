CREATE TABLE IF NOT EXISTS price_off_location_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT uk_price_off_location_code UNIQUE (code)
);

CREATE INDEX idx_price_off_location_code ON price_off_location_master (code);
CREATE INDEX idx_price_off_location_active ON price_off_location_master (is_active);

INSERT INTO price_off_location_master (name, code, is_active)
SELECT state_name, state_code, is_active
FROM state_master
WHERE state_code IS NOT NULL
  AND state_code <> ''
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    is_active = VALUES(is_active);

ALTER TABLE consumer_price_off_campaign
    ADD COLUMN IF NOT EXISTS total_qty INT NULL,
    ADD COLUMN IF NOT EXISTS credit_note DECIMAL(12, 4) NULL;

CREATE TABLE IF NOT EXISTS consumer_price_off_campaign_location_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    allocated_qty INT NOT NULL,
    CONSTRAINT fk_cpo_loc_alloc_campaign
        FOREIGN KEY (campaign_id) REFERENCES consumer_price_off_campaign (id) ON DELETE CASCADE,
    CONSTRAINT uk_cpo_loc_alloc_campaign_code UNIQUE (campaign_id, location_code)
);

CREATE INDEX idx_cpo_loc_alloc_campaign ON consumer_price_off_campaign_location_allocation (campaign_id);
