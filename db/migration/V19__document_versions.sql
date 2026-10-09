CREATE TABLE document_version (
    document_id BIGINT NOT NULL REFERENCES document_metadata(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    file_name VARCHAR(500) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    sha256 CHAR(64) NOT NULL,
    uploaded_by VARCHAR(150),
    change_note VARCHAR(1000),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (document_id, version_number)
);

INSERT INTO document_version (document_id, version_number, file_name, object_key,
                              mime_type, file_size, sha256, uploaded_by, uploaded_at)
SELECT id, 1, original_name, local_path, mime_type, file_size, sha256,
       uploader_username, COALESCE(uploaded_at, created_at, CURRENT_TIMESTAMP)
FROM document_metadata
WHERE local_path ~ '^documents/doc_[a-f0-9]{32}[.](pdf|doc|docx|xls|xlsx|dwg|jpg|jpeg|png|zip)$'
  AND file_size > 0 AND mime_type IS NOT NULL AND sha256 ~ '^[a-f0-9]{64}$';
