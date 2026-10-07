-- ==============================================================================
-- V4: KHỞI TẠO TẦNG DANH MỤC THAM CHIẾU VÀ HỒ SƠ TÀI LIỆU
-- ==============================================================================

-- 1. Bảng danh mục tham chiếu chuẩn hóa (reference_catalog)
CREATE TABLE reference_catalog (
    id BIGSERIAL PRIMARY KEY,
    catalog_code VARCHAR(100) NOT NULL,              -- Mã danh mục (VD: 'capduong', 'road_sign_catalog')
    item_code VARCHAR(100) NOT NULL,                 -- Mã phần tử (VD: '03', 'W.247')
    item_name VARCHAR(255) NOT NULL,                 -- Tên hiển thị tiếng Việt (VD: 'Cấp III', 'Chú ý xe đỗ')
    item_name_en VARCHAR(255),                       -- Tên tiếng Anh
    parent_code VARCHAR(100),                        -- Mã phần tử cha trong danh mục phân cấp
    sort_order INTEGER DEFAULT 0,                    -- Thứ tự hiển thị
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    organization_id VARCHAR(100) DEFAULT 'moc_dbvn',
    extra_attributes JSONB DEFAULT '{}'::jsonb,      -- Kích thước biển, màu sắc, hình dạng, quy chuẩn
    
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_reference_catalog UNIQUE (catalog_code, item_code)
);

CREATE INDEX idx_ref_catalog_lookup ON reference_catalog(catalog_code, sort_order) WHERE is_deleted = FALSE;
CREATE INDEX idx_ref_catalog_parent ON reference_catalog(catalog_code, parent_code) WHERE is_deleted = FALSE;

-- 2. Bảng thư mục hồ sơ (document_folder)
CREATE TABLE document_folder (
    id BIGSERIAL PRIMARY KEY,
    folder_code VARCHAR(100) NOT NULL UNIQUE,        -- Mã thư mục (VD: 'cdb_vn', 'group_10')
    folder_name VARCHAR(255) NOT NULL,               -- Tên thư mục hiển thị
    parent_folder_id BIGINT REFERENCES document_folder(id) ON DELETE SET NULL,
    organization_id VARCHAR(100) DEFAULT 'moc_dbvn',
    sort_order INTEGER DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_folder_tree ON document_folder(parent_folder_id) WHERE is_deleted = FALSE;

-- 3. Bảng siêu dữ liệu hồ sơ tài liệu (document_metadata)
CREATE TABLE document_metadata (
    id BIGSERIAL PRIMARY KEY,
    file_entry_id VARCHAR(150) NOT NULL UNIQUE,      -- ID tệp nguồn (VD: 'cbfc95cca388487497048566b5d93252')
    original_name VARCHAR(500) NOT NULL,             -- Tên tệp gốc kèm phần mở rộng
    file_extension VARCHAR(50),                      -- 'pdf', 'xlsx', 'docx', 'dwg', 'jpg'
    mime_type VARCHAR(150),                          -- MIME type chuẩn
    file_size BIGINT,                                -- Kích thước byte
    local_path VARCHAR(500) NOT NULL,                -- Đường dẫn lưu trên đĩa
    sha256 CHAR(64) NOT NULL,                        -- Băm SHA-256 xác thực tệp
    
    folder_id BIGINT REFERENCES document_folder(id) ON DELETE SET NULL,
    asset_record_id VARCHAR(150),                    -- Tham chiếu nghiệp vụ tới asset_record.record_id
    
    organization_id VARCHAR(100) DEFAULT 'moc_dbvn',
    branch_id VARCHAR(100),
    uploader_username VARCHAR(150),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_meta_asset ON document_metadata(asset_record_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_doc_meta_folder ON document_metadata(folder_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_doc_meta_sha256 ON document_metadata(sha256);
