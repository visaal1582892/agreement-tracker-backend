DROP TABLE IF EXISTS agreement_states;
ALTER TABLE agreements DROP COLUMN partner_state_code;
ALTER TABLE agreements DROP COLUMN partner_state_name;
ALTER TABLE agreements DROP COLUMN partner_city_code;
ALTER TABLE agreements DROP COLUMN partner_city_name;
