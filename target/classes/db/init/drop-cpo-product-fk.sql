-- Drop legacy product_master FK before Hibernate alters product_id (continue-on-error if absent)
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY FKm4yn92wkvuxfucupd0cq6ue7f;
ALTER TABLE consumer_price_off_campaign DROP FOREIGN KEY fk_cpo_product;
