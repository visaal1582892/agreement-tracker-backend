-- Add city column for payout geo filters (city-only must not expand to parent state).
-- continue-on-error=true in application.properties covers re-runs.
ALTER TABLE mock_purchase_data ADD COLUMN city VARCHAR(10) NULL;
