-- Drop obsolete flat JSON location columns from agreements table.
-- These are superseded by the normalized agreement_locations table.
ALTER TABLE agreements DROP COLUMN IF EXISTS geography_mode;
ALTER TABLE agreements DROP COLUMN IF EXISTS partner_states_json;
ALTER TABLE agreements DROP COLUMN IF EXISTS partner_cities_json;
