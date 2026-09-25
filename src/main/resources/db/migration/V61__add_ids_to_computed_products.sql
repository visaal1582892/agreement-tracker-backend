ALTER TABLE agreement_computed_products
ADD COLUMN manufacturer_id VARCHAR(100),
ADD COLUMN division_id VARCHAR(100);

CREATE INDEX idx_acp_manufacturer_id ON agreement_computed_products (manufacturer_id);
CREATE INDEX idx_acp_division_id ON agreement_computed_products (division_id);
