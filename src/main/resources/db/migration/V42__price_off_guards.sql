ALTER TABLE product_master
    ADD COLUMN cp DECIMAL(12, 4) NULL AFTER mrp;

ALTER TABLE consumer_price_off_campaign
    ADD COLUMN is_negative_margin BIT NOT NULL DEFAULT 0 AFTER final_margin_percent;

CREATE INDEX idx_cpo_product_dates ON consumer_price_off_campaign (product_id, start_date, end_date);

UPDATE product_master
SET cp = ROUND(mrp * 0.75, 4)
WHERE cp IS NULL AND mrp IS NOT NULL;
