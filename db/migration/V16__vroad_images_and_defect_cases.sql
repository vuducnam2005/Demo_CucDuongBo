CREATE TABLE vroad_image_reference (
    dataset_key VARCHAR(100) NOT NULL,
    record_key VARCHAR(150) NOT NULL,
    source_url TEXT NOT NULL,
    source_report VARCHAR(255) NOT NULL,
    workbook_sha256 CHAR(64) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dataset_key, record_key),
    FOREIGN KEY (dataset_key, record_key)
        REFERENCES raw_dataset_record(dataset_key, record_key) ON DELETE RESTRICT,
    CONSTRAINT ck_vroad_image_source CHECK (source_url LIKE 'https://platform.vroad.vn/%')
);

CREATE TABLE vroad_defect_case (
    defect_record_id BIGINT PRIMARY KEY REFERENCES raw_dataset_record(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'RESOLVED',
    resolution_note VARCHAR(2000) NOT NULL,
    resolved_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_vroad_case_status CHECK (status = 'RESOLVED'),
    CONSTRAINT ck_vroad_case_note CHECK (char_length(trim(resolution_note)) >= 5)
);

CREATE INDEX idx_vroad_case_resolved_at ON vroad_defect_case(resolved_at DESC);

CREATE TABLE vroad_case_evidence (
    defect_record_id BIGINT PRIMARY KEY REFERENCES vroad_defect_case(defect_record_id) ON DELETE RESTRICT,
    media_type VARCHAR(30) NOT NULL,
    content BYTEA NOT NULL,
    sha256 CHAR(64) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_vroad_evidence_media CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_vroad_evidence_size CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);
