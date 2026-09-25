-- Price Off campaigns: support EXPIRED approval status (stored as VARCHAR, no enum alteration needed).
-- Backfill campaigns whose end date has already passed.
UPDATE consumer_price_off_campaign
SET approval_status = 'EXPIRED'
WHERE approval_status = 'APPROVED'
  AND end_date < CURDATE();
