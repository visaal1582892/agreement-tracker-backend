CREATE TABLE revenue_recognition_settings (
    id BIGSERIAL PRIMARY KEY,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cron_expression VARCHAR(255) NOT NULL DEFAULT '0 0 1 1 * ?', 
    time_zone VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',
    lookback_window_months INT NOT NULL DEFAULT 1,
    last_successful_run TIMESTAMP,
    last_failed_run TIMESTAMP,
    current_status VARCHAR(50) DEFAULT 'IDLE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

-- Insert a default row
INSERT INTO revenue_recognition_settings (id, is_enabled, cron_expression) VALUES (1, false, '0 0 1 1 * ?');
