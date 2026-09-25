CREATE TABLE agreement_monthly_payable_rollup (
    id BIGSERIAL PRIMARY KEY,
    agreement_version_id BIGINT NOT NULL,
    agreement_id BIGINT NOT NULL,
    supplier_id BIGINT,
    calendar_year INT NOT NULL,
    calendar_month INT NOT NULL,
    triggered_frequencies VARCHAR(255),
    earned_amount NUMERIC(15, 2),
    payable_amount NUMERIC(15, 2),
    payment_interval VARCHAR(50),
    calculated_at TIMESTAMP,
    created_by VARCHAR(255),
    created_date TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP
);

CREATE INDEX idx_ampr_agreement_id ON agreement_monthly_payable_rollup(agreement_id);
CREATE INDEX idx_ampr_agreement_version_id ON agreement_monthly_payable_rollup(agreement_version_id);
CREATE INDEX idx_ampr_calendar ON agreement_monthly_payable_rollup(calendar_year, calendar_month);
