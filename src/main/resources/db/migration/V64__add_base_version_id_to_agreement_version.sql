ALTER TABLE agreement_versions ADD COLUMN base_version_id BIGINT;
ALTER TABLE agreement_versions ADD CONSTRAINT fk_agreement_base_version FOREIGN KEY (base_version_id) REFERENCES agreement_versions(id);
