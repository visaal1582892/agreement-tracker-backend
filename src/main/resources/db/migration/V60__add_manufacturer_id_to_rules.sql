ALTER TABLE agreement_division_rules
ADD COLUMN manufacturer_id BIGINT;

ALTER TABLE agreement_product_rules
ADD COLUMN manufacturer_id BIGINT;
