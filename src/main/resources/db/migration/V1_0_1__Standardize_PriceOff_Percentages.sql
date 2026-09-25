UPDATE consumer_price_off_campaign
SET 
  margin_percent = margin_percent * 100,
  final_margin_percent = final_margin_percent * 100,
  percent_off = percent_off * 100,
  base_offer = CASE WHEN discount_type = 'DISC_PERCENT' THEN base_offer * 100 ELSE base_offer END,
  final_offer = CASE WHEN discount_type = 'DISC_PERCENT' THEN final_offer * 100 ELSE final_offer END
WHERE margin_percent <= 1.0 AND margin_percent > -1.0;
