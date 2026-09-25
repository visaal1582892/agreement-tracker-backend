-- Payout calculator: independent city filter on mock purchase rows.
ALTER TABLE mock_purchase_data
    ADD COLUMN city VARCHAR(10) NULL;
