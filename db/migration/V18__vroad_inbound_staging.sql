CREATE TABLE vroad_ingest_batch (
    id BIGSERIAL PRIMARY KEY,
    request_key VARCHAR(120) NOT NULL UNIQUE,
    schema_version VARCHAR(40) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    submitted_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'STAGED' CHECK (status = 'STAGED'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vroad_ingest_record (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES vroad_ingest_batch(id) ON DELETE RESTRICT,
    source_record_key VARCHAR(150),
    record_kind VARCHAR(40),
    payload JSONB NOT NULL,
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'INVALID'))
);

CREATE INDEX idx_vroad_ingest_record_batch ON vroad_ingest_record(batch_id, id);
