-- ==============================================================================
-- V2: KHỞI TẠO TẦNG RAW INGESTION VÀ QUẢN LÝ TIẾN TRÌNH NHẬP LIỆU
-- ==============================================================================

-- 1. Bảng theo dõi phiên nạp (import_job)
CREATE TABLE import_job (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(150) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',   -- 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'
    total_files INTEGER DEFAULT 0,
    processed_files INTEGER DEFAULT 0,
    total_records INTEGER DEFAULT 0,
    success_records INTEGER DEFAULT 0,
    error_records INTEGER DEFAULT 0,
    notes TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_import_job_status ON import_job(status);
CREATE INDEX idx_import_job_started ON import_job(started_at DESC);

-- 2. Bảng theo dõi tệp nguồn (import_file)
CREATE TABLE import_file (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES import_job(id) ON DELETE CASCADE,
    file_path VARCHAR(255) NOT NULL,                 -- Đường dẫn tương đối (VD: 'assets/duonggom.json')
    file_bytes BIGINT NOT NULL,                      -- Kích thước tệp (bytes)
    source_sha256 CHAR(64) NOT NULL,                 -- Băm SHA-256 của file nguồn
    record_count INTEGER DEFAULT 0,                  -- Số bản ghi ghi nhận trong manifest
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',   -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_import_file_job_path UNIQUE (job_id, file_path)
);

CREATE INDEX idx_import_file_status ON import_file(status);
CREATE INDEX idx_import_file_sha256 ON import_file(source_sha256);

-- 3. Bảng đăng ký tập dữ liệu (dataset_registry)
CREATE TABLE dataset_registry (
    id BIGSERIAL PRIMARY KEY,
    dataset_key VARCHAR(100) NOT NULL UNIQUE,        -- Mã dataset (VD: 'duonggom', 'tbl_bridge')
    dataset_name VARCHAR(255) NOT NULL,              -- Tên hiển thị tiếng Việt
    kind VARCHAR(50) NOT NULL,                       -- 'asset', 'reference', 'document', 'report', 'statistic', 'auxiliary'
    endpoint VARCHAR(255),                           -- Endpoint nguồn crawl
    source_file VARCHAR(255) NOT NULL,               -- Tên file json tương ứng
    total_records INTEGER DEFAULT 0,                 -- Số bản ghi ghi nhận
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dataset_registry_kind ON dataset_registry(kind);

-- 4. Bảng từ điển trường dữ liệu (dataset_field)
CREATE TABLE dataset_field (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES dataset_registry(id) ON DELETE CASCADE,
    field_name VARCHAR(150) NOT NULL,               -- Tên trường trong nguồn (VD: 'gid', 'field.km_from')
    field_alias VARCHAR(255),                        -- Tên diễn giải tiếng Việt
    data_type VARCHAR(50) DEFAULT 'varchar',         -- Kiểu dữ liệu nhận diện
    is_searchable BOOLEAN DEFAULT FALSE,
    is_filter BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dataset_field UNIQUE (dataset_id, field_name)
);

CREATE INDEX idx_dataset_field_lookup ON dataset_field(dataset_id, field_name);

-- 5. Bảng lưu trữ bản ghi thô (raw_dataset_record)
CREATE TABLE raw_dataset_record (
    id BIGSERIAL PRIMARY KEY,
    dataset_key VARCHAR(100) NOT NULL REFERENCES dataset_registry(dataset_key) ON UPDATE CASCADE,
    record_key VARCHAR(150) NOT NULL,                -- Khóa nhận diện trong JSON ('id' hoặc 'gid')
    raw_payload JSONB NOT NULL,                      -- Toàn bộ JSON object gốc
    source_file VARCHAR(255) NOT NULL,               -- Đường dẫn tệp nguồn
    source_sha256 CHAR(64) NOT NULL,                 -- Băm SHA-256 của file nguồn
    payload_sha256 CHAR(64) NOT NULL,                -- Băm SHA-256 của chuỗi payload
    import_job_id BIGINT REFERENCES import_job(id) ON DELETE SET NULL,
    record_status VARCHAR(50) NOT NULL DEFAULT 'RAW_STORED', -- 'RAW_STORED', 'CURATED', 'PARSE_FAILED', 'SKIPPED', 'STALE'
    imported_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Ràng buộc chống trùng lặp dữ liệu thô
    CONSTRAINT uq_raw_dataset_record UNIQUE (dataset_key, record_key)
);

CREATE INDEX idx_raw_dataset_status ON raw_dataset_record(dataset_key, record_status);
CREATE INDEX idx_raw_payload_sha256 ON raw_dataset_record(payload_sha256);
CREATE INDEX idx_raw_record_key ON raw_dataset_record(record_key);
CREATE INDEX idx_raw_job_id ON raw_dataset_record(import_job_id);
CREATE INDEX idx_raw_imported_at ON raw_dataset_record(imported_at DESC);
CREATE INDEX idx_raw_payload_gin ON raw_dataset_record USING GIN (raw_payload jsonb_path_ops);

-- 6. Bảng nhật ký lỗi import (import_error)
CREATE TABLE import_error (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES import_job(id) ON DELETE CASCADE,
    file_id BIGINT REFERENCES import_file(id) ON DELETE CASCADE,
    dataset_key VARCHAR(100),
    record_key VARCHAR(150),
    error_stage VARCHAR(50) NOT NULL,                -- 'FILE_READ', 'RAW_INGEST', 'PARSE_JSON', 'CURATION_TRANSFER'
    error_code VARCHAR(100),
    error_message TEXT NOT NULL,
    raw_fragment JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_import_error_job ON import_error(job_id);
CREATE INDEX idx_import_error_dataset ON import_error(dataset_key);
