ALTER TABLE agreements
    ADD COLUMN geography_mode VARCHAR(16) NULL,
    ADD COLUMN partner_states_json TEXT NULL,
    ADD COLUMN partner_cities_json TEXT NULL;

DROP TABLE IF EXISTS agreement_states;
