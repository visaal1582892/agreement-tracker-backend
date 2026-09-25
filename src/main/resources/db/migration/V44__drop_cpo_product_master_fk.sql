-- Safety migration when FK drop was missed before VARCHAR alter
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY FKm4yn92wkvuxfucupd0cq6ue7f;
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY fk_cpo_product;

ALTER TABLE consumer_price_off_campaign
    MODIFY COLUMN product_id VARCHAR(50) NOT NULL;
