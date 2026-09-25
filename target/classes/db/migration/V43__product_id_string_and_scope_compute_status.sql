-- Dev wipe approved: truncate dependent rows before schema change
TRUNCATE TABLE agreement_computed_products;
TRUNCATE TABLE agreement_product_rules;
TRUNCATE TABLE consumer_price_off_campaign_location_allocations;
TRUNCATE TABLE consumer_price_off_campaign_states;
TRUNCATE TABLE consumer_price_off_campaign_channels;
TRUNCATE TABLE consumer_price_off_campaign;

ALTER TABLE agreement_product_rules
    MODIFY COLUMN product_id VARCHAR(50) NOT NULL;

ALTER TABLE agreement_computed_products
    MODIFY COLUMN product_id VARCHAR(50) NOT NULL;

-- Drop legacy FK to product_master (Hibernate auto-name + explicit name)
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY FKm4yn92wkvuxfucupd0cq6ue7f;
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY fk_cpo_product;

ALTER TABLE consumer_price_off_campaign
    MODIFY COLUMN product_id VARCHAR(50) NOT NULL;

ALTER TABLE agreement_versions
    ADD COLUMN product_scope_compute_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    ADD COLUMN product_scope_compute_error VARCHAR(1000) NULL;

CREATE INDEX idx_av_product_scope_compute_status ON agreement_versions (product_scope_compute_status);
