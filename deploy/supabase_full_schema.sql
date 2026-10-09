-- ==============================================================================
-- V1: KHỞI TẠO TIỆN ÍCH MỞ RỘNG VÀ HỆ THỐNG QUẢN TRỊ BẢO MẬT
-- ==============================================================================

-- 1. Khởi tạo tiện ích mở rộng PostGIS và Trigram
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Thiết lập timezone mặc định cho session
SET timezone = 'UTC';

-- 3. Bảng vai trò người dùng (app_role)
CREATE TABLE app_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,           -- 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER'
    role_name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng tài khoản người dùng (app_user)
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES app_role(id) ON DELETE RESTRICT,
    organization_id VARCHAR(100) DEFAULT 'moc_dbvn',
    branch_id VARCHAR(100),                          -- Giới hạn quyền truy cập theo chi nhánh (VD: 'kqldb_1')
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_user_branch ON app_user(branch_id);

-- 5. Bảng phân quyền chi tiết (app_permission)
CREATE TABLE app_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,           -- 'ASSET:READ', 'ASSET:WRITE', 'DOCUMENT:DOWNLOAD'
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    CONSTRAINT uq_app_permission UNIQUE (role_id, permission_code)
);

-- 6. Bảng nhật ký kiểm toán (audit_log)
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,                    -- 'CREATE', 'UPDATE', 'DELETE', 'EXPORT'
    entity_type VARCHAR(100) NOT NULL,               -- 'ASSET', 'DOCUMENT', 'USER'
    entity_id VARCHAR(150) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);


-- ==========================================

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


-- ==========================================

-- ==============================================================================
-- V3: KHỞI TẠO TẦNG TÀI SẢN VẬN HÀNH (ASSET_RECORD) VÀ HÌNH HỌC (ASSET_GEOMETRY)
-- ==============================================================================

-- 1. Bảng lõi tài sản hạ tầng đường bộ (asset_record)
CREATE TABLE asset_record (
    id BIGSERIAL PRIMARY KEY,
    
    -- Liên kết dữ liệu thô (Data Lineage)
    raw_record_id BIGINT REFERENCES raw_dataset_record(id) ON DELETE SET NULL,
    
    -- Khóa định danh nghiệp vụ
    record_id VARCHAR(150) NOT NULL UNIQUE,          -- Trường 'id' từ nguồn JSON (VD: 'bridge_45228')
    gid BIGINT,                                      -- Khóa định danh số nguyên gốc
    
    -- Phân loại
    dataset_code VARCHAR(100) NOT NULL,              -- Mã bảng nguồn (VD: 'tbl_bridge', 'tbl_road_sign')
    asset_type VARCHAR(100) NOT NULL,                -- Phân nhóm chuẩn: 'BRIDGE', 'ROAD_SIGN', 'DRAINAGE', v.v.
    name VARCHAR(500) NOT NULL,                      -- Tên công trình hiển thị
    
    -- Tuyến và lý trình (Linear Referencing)
    route_code VARCHAR(100),                         -- Mã tuyến quốc lộ / tỉnh lộ (VD: 'QL.1', 'QL.2')
    route_name VARCHAR(255),                         -- Tên tuyến
    km_from NUMERIC(10, 3),                          -- Lý trình điểm đầu (km số, VD: 128.000)
    km_to NUMERIC(10, 3),                            -- Lý trình điểm cuối (km số, VD: 139.771)
    lytrinh VARCHAR(100),                            -- Chuỗi hiển thị lý trình sạch (VD: 'Km 128 + 000')
    
    -- Hành chính & Kỹ thuật
    province_id VARCHAR(100),                        -- Mã / Tên tỉnh thành điểm đặt tài sản
    province_name VARCHAR(150),                      -- Tên tỉnh thành phố
    district_name VARCHAR(150),                      -- Tên quận/huyện
    town_name VARCHAR(150),                          -- Tên xã/phường
    road_class VARCHAR(100),                         -- Cấp kỹ thuật đường (VD: 'Cấp III', 'Đường cao tốc')
    road_type VARCHAR(100),                          -- Loại đường (VD: 'Quốc lộ', 'Đường gom')
    
    -- Đơn vị quản lý và phân quyền
    organization_id VARCHAR(100) DEFAULT 'moc_dbvn', -- Tổ chức chủ quản
    branch_id VARCHAR(100),                          -- Mã chi nhánh quản lý (VD: 'kqldb_1', 'sxd_tq')
    branch_name VARCHAR(255),                        -- Tên hiển thị chi nhánh
    management_agency VARCHAR(255),                  -- Hạt / Đội quản lý bảo trì trực tiếp
    
    -- Vòng đời và trạng thái vận hành
    state VARCHAR(50) DEFAULT 'Approved',            -- Trạng thái duyệt ('Approved', 'Pending', 'Drafting')
    state_name VARCHAR(100) DEFAULT 'Đã duyệt',      -- Tên hiển thị trạng thái tiếng Việt
    level_state VARCHAR(50) DEFAULT 'cdb_vn',        -- Cấp phê duyệt
    active_status VARCHAR(100),                      -- Tình trạng khai thác
    maintain_value NUMERIC(18, 2) DEFAULT 0,         -- Giá trị bảo trì ghi nhận (VNĐ)
    construction_year INTEGER,                       -- Năm xây dựng / đưa vào khai thác
    
    -- Quan hệ phân cấp hạ tầng (Cha - Con)
    parent_id VARCHAR(150),                          -- ID tài sản cha (đã cắt bỏ hậu tố bảng con)
    
    -- Thuộc tính đặc thù chi tiết (Domain Attributes)
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,   -- Chứa các thuộc tính động đã làm sạch
    
    -- Xóa mềm (Soft Delete)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    
    -- Phiên bản (Optimistic Locking)
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Ràng buộc kiểm tra thuộc tính JSONB bắt buộc là JSON Object
    CONSTRAINT chk_asset_attributes_is_object CHECK (jsonb_typeof(attributes) = 'object')
);

-- Chỉ mục lọc và phân trang hiệu năng cao (kết hợp Partial Index WHERE is_deleted = FALSE):
CREATE INDEX idx_asset_record_lookup ON asset_record(dataset_code, is_deleted);
CREATE INDEX idx_asset_record_type ON asset_record(asset_type, is_deleted);
CREATE INDEX idx_asset_record_route_km ON asset_record(route_code, km_from, km_to) WHERE is_deleted = FALSE;
CREATE INDEX idx_asset_record_scope ON asset_record(branch_id, province_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_asset_record_state ON asset_record(state) WHERE is_deleted = FALSE;
CREATE INDEX idx_asset_record_parent ON asset_record(parent_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_asset_record_raw_link ON asset_record(raw_record_id);

-- Chỉ mục GIN JSONB Path Ops phục vụ tìm kiếm cặp key-value động:
CREATE INDEX idx_asset_record_attrs_gin ON asset_record USING GIN (attributes jsonb_path_ops);

-- Chỉ mục Trigram phục vụ tìm kiếm mờ tiếng Việt:
CREATE INDEX idx_asset_record_name_trgm ON asset_record USING GIN (name gin_trgm_ops);


-- 2. Bảng hình học không gian PostGIS (asset_geometry)
CREATE TABLE asset_geometry (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL UNIQUE REFERENCES asset_record(id) ON DELETE CASCADE,
    geom_type VARCHAR(50) NOT NULL,                  -- 'POINT', 'LINESTRING', 'POLYGON'
    
    -- Cột hình học chuẩn PostGIS WGS 84
    geom GEOMETRY(Geometry, 4326) NOT NULL,          -- Cột hình học tổng quát
    point_geom GEOMETRY(Point, 4326),                -- Cột riêng cho điểm (Point)
    line_geom GEOMETRY(LineString, 4326),            -- Cột riêng cho đường (LineString)
    
    -- Tọa độ hộp bao Bounding Box hỗ trợ truy vấn khung nhìn viewport
    bbox_xmin NUMERIC(12, 8),
    bbox_ymin NUMERIC(12, 8),
    bbox_xmax NUMERIC(12, 8),
    bbox_ymax NUMERIC(12, 8),
    
    srid INTEGER NOT NULL DEFAULT 4326,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,          -- Đánh dấu FALSE nếu tọa độ = 0 hoặc ngoài lãnh thổ VN
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ràng buộc kiểm tra kiểu hình học hợp lệ
    CONSTRAINT chk_asset_geom_type CHECK (geom_type IN ('POINT', 'LINESTRING', 'POLYGON', 'MULTIPOINT', 'MULTILINESTRING', 'MULTIPOLYGON'))
);

-- Chỉ mục không gian GiST bắt buộc cho bản đồ WebGIS:
CREATE INDEX idx_asset_geom_gist ON asset_geometry USING GIST (geom);
CREATE INDEX idx_asset_geom_point_gist ON asset_geometry USING GIST (point_geom) WHERE point_geom IS NOT NULL;
CREATE INDEX idx_asset_geom_line_gist ON asset_geometry USING GIST (line_geom) WHERE line_geom IS NOT NULL;

-- Chỉ mục B-Tree trên bounding box:
CREATE INDEX idx_asset_geom_bbox ON asset_geometry(bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax);


-- ==========================================

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


-- ==========================================

-- ==============================================================================
-- V5: KHỞI TẠO CÁC SQL VIEW BÁO CÁO BẢO TRÌ ĐỘNG
-- ==============================================================================

-- 1. View Báo cáo Chi tiết Bảo trì Tài sản (thay thế 148 bảng maintenance_detail_*_chitiet)
CREATE OR REPLACE VIEW view_maintenance_detail_report AS
SELECT 
    ar.id AS asset_id,
    ar.record_id AS vidagis_id,
    ar.dataset_code,
    ar.asset_type,
    ar.name,
    ar.route_code,
    ar.lytrinh,
    ar.km_from,
    ar.km_to,
    ar.province_id,
    ar.province_name,
    ar.branch_id,
    ar.branch_name,
    ar.active_status,
    ar.maintain_value,
    ar.state,
    ar.state_name,
    ar.attributes
FROM asset_record ar
WHERE ar.is_deleted = FALSE;

-- 2. View Báo cáo Tổng hợp Bảo trì Tài sản (thay thế 148 bảng maintenance_detail_*_tonghop)
CREATE OR REPLACE VIEW view_maintenance_summary_report AS
SELECT 
    ar.dataset_code,
    ar.asset_type,
    ar.branch_id,
    ar.branch_name,
    ar.route_code,
    ar.province_name,
    COUNT(ar.id) AS total_count,
    SUM(COALESCE(ar.km_to - ar.km_from, 0)) AS total_length_km,
    SUM(COALESCE(ar.maintain_value, 0)) AS total_maintain_cost,
    COUNT(CASE WHEN ar.state = 'Approved' THEN 1 END) AS approved_count,
    COUNT(CASE WHEN ar.state = 'Pending' THEN 1 END) AS pending_count
FROM asset_record ar
WHERE ar.is_deleted = FALSE
GROUP BY ar.dataset_code, ar.asset_type, ar.branch_id, ar.branch_name, ar.route_code, ar.province_name;


-- ==========================================

-- ==============================================================================
-- V6: BỔ SUNG BẢNG LƯU TRỮ REFRESH TOKEN VÀ KHỞI TẠO DỮ LIỆU PHÂN QUYỀN RBAC
-- ==============================================================================

-- 1. Bảng lưu trữ Refresh Token bảo mật (có cơ chế xoay vòng và thu hồi)
CREATE TABLE IF NOT EXISTS app_refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,          -- Mã băm SHA-256 của refresh token (không lưu plain-text)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON app_refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON app_refresh_token(token_hash);

-- 2. Khởi tạo 4 vai trò chuẩn trong hệ thống (RBAC)
INSERT INTO app_role (id, role_code, role_name, description) VALUES
(1, 'ROLE_VIEWER', 'Cán bộ Tra cứu', 'Chỉ đọc (Read-only): Xem Dashboard, tra cứu danh mục, tìm kiếm công trình, WebGIS, xem hồ sơ kỹ thuật.'),
(2, 'ROLE_EDITOR', 'Cán bộ Kỹ thuật / Nhập liệu', 'Quyền xem và cập nhật thông tin kỹ thuật công trình, bổ sung lý trình, đính kèm hồ sơ kỹ thuật.'),
(3, 'ROLE_MANAGER', 'Cán bộ Quản lý / Lãnh đạo', 'Phê duyệt kết quả kiểm tra định kỳ, kế hoạch bảo trì, xuất báo cáo tổng hợp, xóa mềm công trình.'),
(4, 'ROLE_ADMIN', 'Quản trị viên Toàn quyền', 'Toàn quyền trên mọi phân hệ (Superuser): Quản lý người dùng, gán vai trò, xem Audit Log, cấu hình hệ thống.')
ON CONFLICT (id) DO UPDATE SET
    role_code = EXCLUDED.role_code,
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description;

SELECT setval('app_role_id_seq', (SELECT COALESCE(MAX(id), 1) FROM app_role));

-- 3. Khởi tạo quyền hạn chi tiết gắn theo module/dataset/action
-- Quyền cho ROLE_VIEWER (id = 1)
INSERT INTO app_permission (role_id, permission_code, resource, action) VALUES
(1, 'DASHBOARD:READ', 'DASHBOARD', 'read'),
(1, 'ASSET:READ', 'ASSET', 'read'),
(1, 'ASSET:EXPORT', 'ASSET', 'export'),
(1, 'GIS:READ', 'GIS', 'read'),
(1, 'REPORT:READ', 'REPORT', 'read'),
(1, 'REPORT:EXPORT', 'REPORT', 'export'),
(1, 'DOCUMENT:READ', 'DOCUMENT', 'read'),
(1, 'CATALOG:READ', 'CATALOG', 'read')
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- Quyền cho ROLE_EDITOR (id = 2)
INSERT INTO app_permission (role_id, permission_code, resource, action) VALUES
(2, 'DASHBOARD:READ', 'DASHBOARD', 'read'),
(2, 'ASSET:READ', 'ASSET', 'read'),
(2, 'ASSET:EXPORT', 'ASSET', 'export'),
(2, 'ASSET:CREATE', 'ASSET', 'create'),
(2, 'ASSET:UPDATE', 'ASSET', 'update'),
(2, 'GIS:READ', 'GIS', 'read'),
(2, 'GIS:UPDATE', 'GIS', 'update'),
(2, 'REPORT:READ', 'REPORT', 'read'),
(2, 'REPORT:EXPORT', 'REPORT', 'export'),
(2, 'DOCUMENT:READ', 'DOCUMENT', 'read'),
(2, 'DOCUMENT:CREATE', 'DOCUMENT', 'create'),
(2, 'CATALOG:READ', 'CATALOG', 'read')
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- Quyền cho ROLE_MANAGER (id = 3)
INSERT INTO app_permission (role_id, permission_code, resource, action) VALUES
(3, 'DASHBOARD:READ', 'DASHBOARD', 'read'),
(3, 'ASSET:READ', 'ASSET', 'read'),
(3, 'ASSET:EXPORT', 'ASSET', 'export'),
(3, 'ASSET:CREATE', 'ASSET', 'create'),
(3, 'ASSET:UPDATE', 'ASSET', 'update'),
(3, 'ASSET:DELETE', 'ASSET', 'delete'),
(3, 'GIS:READ', 'GIS', 'read'),
(3, 'GIS:UPDATE', 'GIS', 'update'),
(3, 'REPORT:READ', 'REPORT', 'read'),
(3, 'REPORT:EXPORT', 'REPORT', 'export'),
(3, 'DOCUMENT:READ', 'DOCUMENT', 'read'),
(3, 'DOCUMENT:CREATE', 'DOCUMENT', 'create'),
(3, 'DOCUMENT:DELETE', 'DOCUMENT', 'delete'),
(3, 'CATALOG:READ', 'CATALOG', 'read'),
(3, 'AUDIT:READ', 'AUDIT', 'read')
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- Quyền cho ROLE_ADMIN (id = 4)
INSERT INTO app_permission (role_id, permission_code, resource, action) VALUES
(4, 'DASHBOARD:READ', 'DASHBOARD', 'read'),
(4, 'ASSET:READ', 'ASSET', 'read'),
(4, 'ASSET:EXPORT', 'ASSET', 'export'),
(4, 'ASSET:CREATE', 'ASSET', 'create'),
(4, 'ASSET:UPDATE', 'ASSET', 'update'),
(4, 'ASSET:DELETE', 'ASSET', 'delete'),
(4, 'GIS:READ', 'GIS', 'read'),
(4, 'GIS:UPDATE', 'GIS', 'update'),
(4, 'REPORT:READ', 'REPORT', 'read'),
(4, 'REPORT:EXPORT', 'REPORT', 'export'),
(4, 'DOCUMENT:READ', 'DOCUMENT', 'read'),
(4, 'DOCUMENT:CREATE', 'DOCUMENT', 'create'),
(4, 'DOCUMENT:DELETE', 'DOCUMENT', 'delete'),
(4, 'CATALOG:READ', 'CATALOG', 'read'),
(4, 'CATALOG:CREATE', 'CATALOG', 'create'),
(4, 'CATALOG:UPDATE', 'CATALOG', 'update'),
(4, 'CATALOG:DELETE', 'CATALOG', 'delete'),
(4, 'IMPORT:READ', 'IMPORT', 'read'),
(4, 'IMPORT:CREATE', 'IMPORT', 'create'),
(4, 'IMPORT:DELETE', 'IMPORT', 'delete'),
(4, 'USER:MANAGE_USERS', 'USER', 'manage_users'),
(4, 'USER:READ', 'USER', 'read'),
(4, 'AUDIT:READ', 'AUDIT', 'read')
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- 4. Khởi tạo 4 tài khoản mẫu tương ứng 4 vai trò
-- Mật khẩu mặc định:
-- Demo users are seeded with bcrypt hashes; credentials are delivered out-of-band.
INSERT INTO app_user (id, username, email, password_hash, full_name, role_id, organization_id, branch_id, is_active) VALUES
(1, 'viewer_demo', 'viewer@drvn.gov.vn', '$2a$12$psdnXhrYx9oBjLTRvgFQMeLM9iF5zlfZDf9XJ5qBTQxaWSPSywtzC', 'Cán bộ Tra cứu Thử nghiệm', 1, 'moc_dbvn', NULL, TRUE),
(2, 'editor_demo', 'editor@drvn.gov.vn', '$2a$12$hkLG6.hcTksgcAViOI8kxu8EsYWx/1QjdzkSU56x3w8wC/R4tF0fa', 'Chuyên viên Kỹ thuật Hạt', 2, 'moc_dbvn', 'kqldb_1', TRUE),
(3, 'manager_demo', 'manager@drvn.gov.vn', '$2a$12$UbLQS/hzkwdijWNaS3sMNef1qwpSMIsT7caEJQ6H4m1chvbTIlqwy', 'Lãnh đạo Khu Quản lý ĐB I', 3, 'moc_dbvn', 'kqldb_1', TRUE),
(4, 'admin', 'admin@drvn.gov.vn', '$2a$12$pq8..nzpiF036mfo/GoyrOM1dTE4//37yVt8Slr0pUOoNyhKUgzsW', 'Quản trị viên Hệ thống', 4, 'moc_dbvn', NULL, TRUE)
ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role_id = EXCLUDED.role_id,
    organization_id = EXCLUDED.organization_id,
    branch_id = EXCLUDED.branch_id,
    is_active = EXCLUDED.is_active;

SELECT setval('app_user_id_seq', (SELECT COALESCE(MAX(id), 1) FROM app_user));


-- ==========================================

-- V7__dashboard_aggregations.sql
-- Optimizations and Materialized Views for Server-Side Dashboard Aggregations

-- 1. Index on dataset_key and branch_id for instant filtering
CREATE INDEX IF NOT EXISTS idx_raw_dataset_branch 
ON raw_dataset_record (dataset_key, (raw_payload->>'branch_id'));

-- 2. Materialized view for high-performance branch/province aggregations
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_dashboard_branch_stats AS
SELECT 
    COALESCE(raw_payload->>'branch_id', 'unassigned') AS branch_id,
    COUNT(*) AS total_assets,
    COUNT(*) FILTER (WHERE dataset_key = 'tbl_bridge') AS bridge_count,
    COUNT(*) FILTER (WHERE dataset_key = 'tbl_road_sign') AS road_sign_count,
    COUNT(*) FILTER (WHERE dataset_key = 'mst_national_road') AS national_road_count
FROM raw_dataset_record
GROUP BY 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_dashboard_branch 
ON mv_dashboard_branch_stats (branch_id);

-- 3. View for national road statistics and length calculations
CREATE OR REPLACE VIEW view_dashboard_national_road_stats AS
SELECT 
    r.record_key,
    COALESCE(r.raw_payload->>'fielddisplay', r.raw_payload->>'text', r.record_key) AS route_name,
    NULLIF(elem->>'column_value', '')::numeric AS length_km,
    r.raw_payload->>'branch_id' AS branch_id,
    r.imported_at,
    r.updated_at
FROM raw_dataset_record r,
     jsonb_array_elements((r.raw_payload->>'data_')::jsonb) elem
WHERE r.dataset_key = 'mst_national_road'
  AND elem->>'column_identify' = 'actual_length';

-- 4. Helper function to refresh materialized view concurrently
CREATE OR REPLACE FUNCTION refresh_dashboard_aggregates()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_dashboard_branch_stats;
END;
$$ LANGUAGE plpgsql;


-- ==========================================

-- V8__gis_spatial_indices.sql
-- Optimizations, Safe Geometry Extraction, and Spatial Views for WebGIS Module

-- 1. Immutable helper function to safely extract Point geometry in WGS 84 (EPSG:4326)
CREATE OR REPLACE FUNCTION safe_point(x_val text, y_val text) 
RETURNS geometry AS $func$
DECLARE
    x double precision;
    y double precision;
BEGIN
    IF x_val IS NULL OR y_val IS NULL OR x_val = '' OR y_val = '' OR x_val = 'null' OR y_val = 'null' THEN
        RETURN NULL;
    END IF;
    x := x_val::double precision;
    y := y_val::double precision;
    -- Sanity check coordinates for Vietnam territory (Lat 8.0 to 24.0, Lon 100.0 to 115.0)
    IF x BETWEEN 100.0 AND 115.0 AND y BETWEEN 8.0 AND 24.0 THEN
        RETURN ST_SetSRID(ST_MakePoint(x, y), 4326);
    END IF;
    RETURN NULL;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$func$ LANGUAGE plpgsql IMMUTABLE;

-- 2. GiST Spatial Index on raw_dataset_record for sub-millisecond BBOX filtering
CREATE INDEX IF NOT EXISTS idx_raw_dataset_spatial_point 
ON raw_dataset_record 
USING gist (safe_point(raw_payload->>'x_min', raw_payload->>'y_min')) 
WHERE raw_payload->>'x_min' IS NOT NULL;

-- 3. Composite Index on parent_id to accelerate sign-to-pole spatial joins
CREATE INDEX IF NOT EXISTS idx_raw_dataset_parent_id 
ON raw_dataset_record (dataset_key, (raw_payload->>'parent_id')) 
WHERE dataset_key = 'tbl_road_sign';

-- 4. Spatial View for Road Signs linking to parent Pole coordinates
CREATE OR REPLACE VIEW view_road_signs_spatial AS
SELECT 
    s.id,
    s.record_key,
    s.dataset_key,
    COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
    s.raw_payload->>'branch_id' AS branch_id,
    COALESCE(s.raw_payload->>'state_name', s.record_status, 'Đang khai thác') AS state_name,
    p.record_key AS pole_key,
    COALESCE((p.raw_payload->>'x_min')::double precision, (s.raw_payload->>'x_min')::double precision) AS lon,
    COALESCE((p.raw_payload->>'y_min')::double precision, (s.raw_payload->>'y_min')::double precision) AS lat,
    safe_point(
        COALESCE(p.raw_payload->>'x_min', s.raw_payload->>'x_min'),
        COALESCE(p.raw_payload->>'y_min', s.raw_payload->>'y_min')
    ) AS geom,
    s.raw_payload
FROM raw_dataset_record s
LEFT JOIN raw_dataset_record p 
  ON p.dataset_key = 'road_sphere_mirror' 
 AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
WHERE s.dataset_key = 'tbl_road_sign'
  AND (p.raw_payload->>'x_min' IS NOT NULL OR s.raw_payload->>'x_min' IS NOT NULL);


-- ==========================================

-- V9__performance_and_spatial_optimizations.sql
-- Giai đoạn 13: Tối ưu hóa hiệu năng truy vấn, chỉ mục B-Tree / GiST và tăng tốc BBOX/Pagination

-- 1. Composite Index phục vụ phân trang tức thời trên 1.1 triệu bản ghi thô
-- Cho phép Index-Only Scan và Deferred Join pattern giảm thời gian truy vấn từ 2,100ms xuống < 1ms
CREATE INDEX IF NOT EXISTS idx_raw_dataset_key_id 
ON raw_dataset_record (dataset_key, id);

-- 2. Functional Expression Index cho tọa độ cực điểm của cột biển báo / gương cầu lồi
-- Tăng tốc độ lọc Bounding Box không gian địa lý trên 52.196 cột từ 12.5s xuống < 2ms
CREATE INDEX IF NOT EXISTS idx_road_sphere_coords 
ON raw_dataset_record (
    ((raw_payload->>'x_min')::double precision), 
    ((raw_payload->>'y_min')::double precision)
) 
WHERE dataset_key = 'road_sphere_mirror' AND (raw_payload->>'x_min') IS NOT NULL;

-- 3. Composite Index phục vụ lọc theo đơn vị quản lý đường bộ (branch_id)
CREATE INDEX IF NOT EXISTS idx_raw_dataset_branch_code 
ON raw_dataset_record (dataset_key, (raw_payload->>'branch_id')) 
WHERE (raw_payload->>'branch_id') IS NOT NULL;

-- 4. Composite Index phục vụ lọc theo mã tuyến đường quốc lộ (route_code)
CREATE INDEX IF NOT EXISTS idx_raw_dataset_route_code 
ON raw_dataset_record (dataset_key, (raw_payload->>'route_code')) 
WHERE (raw_payload->>'route_code') IS NOT NULL;

-- 5. Trigram GIN Index cho tìm kiếm mờ tên công trình cầu đường bộ (tbl_bridge)
-- Tăng tốc độ tìm kiếm từ 2,064ms xuống 0.18ms
CREATE INDEX IF NOT EXISTS idx_raw_bridge_name_trgm 
ON raw_dataset_record USING gin (
    ((raw_payload->>'name')::text) gin_trgm_ops
) 
WHERE dataset_key = 'tbl_bridge';

-- 6. Cập nhật thống kê catalog PostgreSQL
ANALYZE raw_dataset_record;


-- ==========================================

-- ==============================================================================
-- V10: KHẮC PHỤC LỖI MÃ HÓA TIẾNG VIỆT (UTF-8) CHO APP_ROLE VÀ APP_USER
-- ==============================================================================

-- 1. Chuẩn hóa tên và mô tả vai trò RBAC (app_role)
UPDATE app_role
SET role_name = 'Cán bộ Tra cứu',
    description = 'Chỉ đọc (Read-only): Xem Dashboard, tra cứu danh mục, tìm kiếm công trình, WebGIS, xem hồ sơ kỹ thuật.'
WHERE id = 1 AND role_code = 'ROLE_VIEWER';

UPDATE app_role
SET role_name = 'Cán bộ Kỹ thuật / Nhập liệu',
    description = 'Quyền xem và cập nhật thông tin kỹ thuật công trình, bổ sung lý trình, đính kèm hồ sơ kỹ thuật.'
WHERE id = 2 AND role_code = 'ROLE_EDITOR';

UPDATE app_role
SET role_name = 'Cán bộ Quản lý / Lãnh đạo',
    description = 'Phê duyệt kết quả kiểm tra định kỳ, kế hoạch bảo trì, xuất báo cáo tổng hợp, xóa mềm công trình.'
WHERE id = 3 AND role_code = 'ROLE_MANAGER';

UPDATE app_role
SET role_name = 'Quản trị viên Toàn quyền',
    description = 'Toàn quyền trên mọi phân hệ (Superuser): Quản lý người dùng, gán vai trò, xem Audit Log, cấu hình hệ thống.'
WHERE id = 4 AND role_code = 'ROLE_ADMIN';

-- 2. Chuẩn hóa họ tên người dùng mặc định (app_user)
UPDATE app_user
SET full_name = 'Cán bộ Tra cứu Thử nghiệm'
WHERE id = 1 AND username = 'viewer_demo';

UPDATE app_user
SET full_name = 'Chuyên viên Kỹ thuật Hạt'
WHERE id = 2 AND username = 'editor_demo';

UPDATE app_user
SET full_name = 'Lãnh đạo Khu Quản lý ĐB I'
WHERE id = 3 AND username = 'manager_demo';

UPDATE app_user
SET full_name = 'Quản trị viên Hệ thống'
WHERE id = 4 AND username = 'admin';


-- ==========================================

-- Keep a narrow, transactionally maintained projection for road-sign map queries.
-- The source JSONB rows are intentionally wide; joining two copies for every map
-- request caused hash spills and 15-30 second cluster response times.

CREATE TABLE IF NOT EXISTS gis_road_sign_point_cache (
    record_key VARCHAR(150) PRIMARY KEY,
    sign_name TEXT NOT NULL,
    branch_id TEXT,
    state_name TEXT,
    route_code TEXT,
    route_name TEXT,
    pole_key VARCHAR(150),
    lon DOUBLE PRECISION NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    geom geometry(Point, 4326) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_geom
    ON gis_road_sign_point_cache USING gist (geom);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_branch
    ON gis_road_sign_point_cache (branch_id);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_state
    ON gis_road_sign_point_cache (state_name);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_route
    ON gis_road_sign_point_cache (route_code);

CREATE INDEX IF NOT EXISTS idx_raw_road_sign_normalized_parent
    ON raw_dataset_record (
        dataset_key,
        regexp_replace(raw_payload->>'parent_id', '_tbl_road_sign$', '')
    )
    WHERE dataset_key = 'tbl_road_sign';

CREATE OR REPLACE FUNCTION kcht_upsert_road_sign_point(p_record_key VARCHAR)
RETURNS VOID AS $$
BEGIN
    DELETE FROM gis_road_sign_point_cache WHERE record_key = p_record_key;

    INSERT INTO gis_road_sign_point_cache (
        record_key, sign_name, branch_id, state_name, route_code, route_name, pole_key,
        lon, lat, geom, refreshed_at
    )
    SELECT
        resolved.record_key,
        resolved.sign_name,
        resolved.branch_id,
        resolved.state_name,
        resolved.route_code,
        resolved.route_name,
        resolved.pole_key,
        ST_X(resolved.geom),
        ST_Y(resolved.geom),
        resolved.geom,
        CURRENT_TIMESTAMP
    FROM (
        SELECT
            s.record_key,
            COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
            s.raw_payload->>'branch_id' AS branch_id,
            COALESCE(s.raw_payload->>'state_name', s.record_status) AS state_name,
            COALESCE(s.raw_payload->>'route_code', s.raw_payload->>'route') AS route_code,
            s.raw_payload->>'route_name' AS route_name,
            regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') AS pole_key,
            COALESCE(
                safe_point(p.raw_payload->>'x_min', p.raw_payload->>'y_min'),
                safe_point(s.raw_payload->>'x_min', s.raw_payload->>'y_min')
            ) AS geom
        FROM raw_dataset_record s
        LEFT JOIN raw_dataset_record p
          ON p.dataset_key = 'road_sphere_mirror'
         AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
        WHERE s.dataset_key = 'tbl_road_sign'
          AND s.record_key = p_record_key
    ) resolved
    WHERE resolved.geom IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION kcht_sync_road_sign_point_cache()
RETURNS TRIGGER AS $$
DECLARE
    affected_key VARCHAR(150);
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.dataset_key = 'tbl_road_sign' THEN
        PERFORM kcht_upsert_road_sign_point(OLD.record_key);
    END IF;

    IF TG_OP <> 'DELETE' AND NEW.dataset_key = 'tbl_road_sign' THEN
        PERFORM kcht_upsert_road_sign_point(NEW.record_key);
    END IF;

    IF TG_OP <> 'INSERT' AND OLD.dataset_key = 'road_sphere_mirror' THEN
        FOR affected_key IN
            SELECT s.record_key
            FROM raw_dataset_record s
            WHERE s.dataset_key = 'tbl_road_sign'
              AND regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') = OLD.record_key
        LOOP
            PERFORM kcht_upsert_road_sign_point(affected_key);
        END LOOP;
    END IF;

    IF TG_OP <> 'DELETE' AND NEW.dataset_key = 'road_sphere_mirror' THEN
        FOR affected_key IN
            SELECT s.record_key
            FROM raw_dataset_record s
            WHERE s.dataset_key = 'tbl_road_sign'
              AND regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') = NEW.record_key
        LOOP
            PERFORM kcht_upsert_road_sign_point(affected_key);
        END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_road_sign_point_cache_write ON raw_dataset_record;
CREATE TRIGGER trg_sync_road_sign_point_cache_write
AFTER INSERT OR UPDATE OF raw_payload, record_status, record_key
ON raw_dataset_record
FOR EACH ROW
WHEN (NEW.dataset_key IN ('tbl_road_sign', 'road_sphere_mirror'))
EXECUTE FUNCTION kcht_sync_road_sign_point_cache();

DROP TRIGGER IF EXISTS trg_sync_road_sign_point_cache_delete ON raw_dataset_record;
CREATE TRIGGER trg_sync_road_sign_point_cache_delete
AFTER DELETE ON raw_dataset_record
FOR EACH ROW
WHEN (OLD.dataset_key IN ('tbl_road_sign', 'road_sphere_mirror'))
EXECUTE FUNCTION kcht_sync_road_sign_point_cache();

INSERT INTO gis_road_sign_point_cache (
    record_key, sign_name, branch_id, state_name, route_code, route_name, pole_key,
    lon, lat, geom, refreshed_at
)
SELECT
    resolved.record_key,
    resolved.sign_name,
    resolved.branch_id,
    resolved.state_name,
    resolved.route_code,
    resolved.route_name,
    resolved.pole_key,
    ST_X(resolved.geom),
    ST_Y(resolved.geom),
    resolved.geom,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        s.record_key,
        COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
        s.raw_payload->>'branch_id' AS branch_id,
        COALESCE(s.raw_payload->>'state_name', s.record_status) AS state_name,
        COALESCE(s.raw_payload->>'route_code', s.raw_payload->>'route') AS route_code,
        s.raw_payload->>'route_name' AS route_name,
        regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') AS pole_key,
        COALESCE(
            safe_point(p.raw_payload->>'x_min', p.raw_payload->>'y_min'),
            safe_point(s.raw_payload->>'x_min', s.raw_payload->>'y_min')
        ) AS geom
    FROM raw_dataset_record s
    LEFT JOIN raw_dataset_record p
      ON p.dataset_key = 'road_sphere_mirror'
     AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
    WHERE s.dataset_key = 'tbl_road_sign'
) resolved
WHERE resolved.geom IS NOT NULL
ON CONFLICT (record_key) DO UPDATE SET
    sign_name = EXCLUDED.sign_name,
    branch_id = EXCLUDED.branch_id,
    state_name = EXCLUDED.state_name,
    route_code = EXCLUDED.route_code,
    route_name = EXCLUDED.route_name,
    pole_key = EXCLUDED.pole_key,
    lon = EXCLUDED.lon,
    lat = EXCLUDED.lat,
    geom = EXCLUDED.geom,
    refreshed_at = EXCLUDED.refreshed_at;

ANALYZE gis_road_sign_point_cache;


-- ==========================================

-- Tăng tốc tìm kiếm mờ đúng theo các trường mà DatasetQueryService expose qua q/keyword.
-- Các index có điều kiện chỉ áp dụng cho dataset cầu đường bộ lớn.
CREATE INDEX IF NOT EXISTS idx_raw_bridge_record_key_trgm
    ON raw_dataset_record USING gin (record_key gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_fielddisplay_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'fielddisplay') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_text_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'text') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_route_name_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'route_name') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

ANALYZE raw_dataset_record;


-- ==========================================

-- Composite trigram expression matching the bridge keyword predicate in DatasetQueryService.
CREATE INDEX IF NOT EXISTS idx_raw_bridge_search_text_trgm
    ON raw_dataset_record USING gin (
        (coalesce(record_key, '') || ' ' || coalesce(raw_payload->>'name', '') || ' ' || coalesce(raw_payload->>'fielddisplay', '') || ' ' || coalesce(raw_payload->>'text', '') || ' ' || coalesce(raw_payload->>'route_name', '')) gin_trgm_ops
    )
    WHERE dataset_key = 'tbl_bridge';

ANALYZE raw_dataset_record;


-- ==========================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ==========================================

CREATE TABLE region_demo_review (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL UNIQUE REFERENCES raw_dataset_record(id) ON DELETE RESTRICT,
    branch_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_REVIEW',
    version INTEGER NOT NULL DEFAULT 1,
    submitted_by BIGINT NOT NULL REFERENCES app_user(id),
    reviewed_by BIGINT REFERENCES app_user(id),
    reason TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_region_demo_review_status CHECK (status IN ('IN_REVIEW', 'APPROVED', 'RETURNED'))
);

CREATE INDEX idx_region_demo_review_branch_status ON region_demo_review(branch_id, status);


-- ==========================================

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


-- ==========================================

CREATE TABLE traffic_station (
    id BIGSERIAL PRIMARY KEY,
    station_code VARCHAR(80) NOT NULL UNIQUE,
    station_name VARCHAR(180) NOT NULL,
    route_name VARCHAR(180) NOT NULL,
    branch_id VARCHAR(100),
    is_demo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_traffic_demo_station CHECK (is_demo)
);

CREATE TABLE traffic_interval (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    direction VARCHAR(50) NOT NULL,
    lane SMALLINT NOT NULL CHECK (lane > 0),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    vehicle_class VARCHAR(40) NOT NULL,
    vehicle_count INTEGER NOT NULL CHECK (vehicle_count >= 0),
    source_event_key VARCHAR(150) NOT NULL UNIQUE,
    CONSTRAINT ck_traffic_half_open_window CHECK (window_start < window_end),
    CONSTRAINT uq_traffic_measure UNIQUE (station_id, direction, lane, window_start, window_end, vehicle_class)
);

CREATE INDEX idx_traffic_window ON traffic_interval(station_id, window_start, window_end);

INSERT INTO traffic_station(station_code, station_name, route_name, is_demo)
VALUES ('DEMO-TRAFFIC-01', 'Trạm mô phỏng số 01', 'Tuyến mô phỏng, không có vị trí thật', TRUE);

INSERT INTO traffic_interval(station_id, direction, lane, window_start, window_end,
                             vehicle_class, vehicle_count, source_event_key)
SELECT station.id, 'Chiều A', 1, date_time, date_time + INTERVAL '15 minutes', vehicle_class,
       CASE vehicle_class WHEN 'Xe máy' THEN 110 + slot * 13
           WHEN 'Ô tô con' THEN 32 + slot * 5
           WHEN 'Xe tải' THEN 15 + slot * 2 ELSE 6 + slot END,
       'DEMO-TRAFFIC-01-' || slot || '-' || vehicle_class
FROM traffic_station station
CROSS JOIN generate_series(0, 7) AS slot
CROSS JOIN (VALUES ('Xe máy'), ('Ô tô con'), ('Xe tải'), ('Xe khách')) AS classes(vehicle_class)
CROSS JOIN LATERAL (SELECT TIMESTAMPTZ '2026-10-07 08:00:00+07' + slot * INTERVAL '15 minutes' AS date_time) AS periods
WHERE station.station_code = 'DEMO-TRAFFIC-01';


-- ==========================================

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


-- ==========================================

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


-- ==========================================

ALTER TABLE traffic_station DROP CONSTRAINT ck_traffic_demo_station;
ALTER TABLE traffic_station ADD COLUMN source_system VARCHAR(80) NOT NULL DEFAULT 'DEMO';
ALTER TABLE traffic_station ADD COLUMN segment_length_km NUMERIC(10, 3)
    CHECK (segment_length_km > 0 AND segment_length_km <= 1000);

CREATE TABLE traffic_import_batch (
    id BIGSERIAL PRIMARY KEY,
    request_key VARCHAR(120) NOT NULL UNIQUE,
    payload_sha256 CHAR(64) NOT NULL,
    source_system VARCHAR(80) NOT NULL,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    submitted_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    interval_count INTEGER NOT NULL,
    snapshot_count INTEGER NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE traffic_import_event (
    source_event_key VARCHAR(80) PRIMARY KEY,
    source_record_key VARCHAR(150) NOT NULL,
    event_kind VARCHAR(20) NOT NULL CHECK (event_kind IN ('COUNT', 'DENSITY')),
    batch_id BIGINT NOT NULL REFERENCES traffic_import_batch(id) ON DELETE RESTRICT
);

CREATE TABLE traffic_density_snapshot (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    direction VARCHAR(50) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    vehicle_class VARCHAR(40) NOT NULL,
    present_vehicles INTEGER NOT NULL CHECK (present_vehicles >= 0),
    source_event_key VARCHAR(80) NOT NULL UNIQUE REFERENCES traffic_import_event(source_event_key),
    CONSTRAINT uq_traffic_density_measure UNIQUE (station_id, direction, measured_at, vehicle_class)
);

CREATE INDEX idx_traffic_density_time ON traffic_density_snapshot(station_id, measured_at DESC);


-- ==========================================

CREATE TABLE vroad_route_assignment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    route_name TEXT NOT NULL,
    chainage_from_m NUMERIC(15,3),
    chainage_to_m NUMERIC(15,3),
    purpose VARCHAR(20) NOT NULL DEFAULT 'DEMO' CHECK (purpose = 'DEMO'),
    assigned_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, route_name),
    CHECK (length(trim(route_name)) BETWEEN 1 AND 500),
    CHECK (chainage_from_m IS NULL OR chainage_from_m >= 0),
    CHECK (chainage_to_m IS NULL OR chainage_to_m >= 0),
    CHECK (chainage_from_m IS NULL OR chainage_to_m IS NULL OR chainage_from_m <= chainage_to_m)
);

CREATE INDEX idx_vroad_route_assignment_route ON vroad_route_assignment(route_name, user_id);
CREATE INDEX idx_vroad_record_route ON raw_dataset_record
    ((COALESCE(raw_payload->>'route_name', raw_payload->>'road_name')))
    WHERE dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri');

CREATE FUNCTION vroad_chainage_m(payload JSONB) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE
        WHEN payload->>'start_m' ~ '^[0-9]+([.][0-9]+)?$'
            THEN (payload->>'start_m')::NUMERIC
        WHEN trim(payload->>'chainage') ~ '^[0-9]+[+/][0-9]{1,3}$'
            THEN split_part(replace(trim(payload->>'chainage'), '/', '+'), '+', 1)::NUMERIC * 1000
                 + split_part(replace(trim(payload->>'chainage'), '/', '+'), '+', 2)::NUMERIC
        ELSE NULL
    END
$$;

CREATE FUNCTION vroad_record_visible(record_id BIGINT, actor_id BIGINT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT COALESCE((
        SELECT actor.is_active AND (
            role.role_code = 'ROLE_ADMIN'
            OR (
                role.role_code IN ('ROLE_EDITOR', 'ROLE_MANAGER', 'ROLE_VIEWER')
                AND NULLIF(trim(actor.branch_id), '') IS NOT NULL
                AND (
                    (item.raw_payload->>'branch_id' = actor.branch_id
                     AND item.dataset_key = 'demo_region_assets')
                    OR (
                        item.dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri')
                        AND EXISTS (
                            SELECT 1 FROM vroad_route_assignment assignment
                            WHERE assignment.user_id = actor.id
                              AND assignment.route_name = COALESCE(item.raw_payload->>'route_name', item.raw_payload->>'road_name')
                              AND (assignment.chainage_from_m IS NULL OR vroad_chainage_m(item.raw_payload) >= assignment.chainage_from_m)
                              AND (assignment.chainage_to_m IS NULL OR
                                  CASE WHEN item.raw_payload->>'end_m' ~ '^[0-9]+([.][0-9]+)?$'
                                       THEN (item.raw_payload->>'end_m')::NUMERIC
                                       ELSE vroad_chainage_m(item.raw_payload) END <= assignment.chainage_to_m)
                        )
                    )
                )
                AND (role.role_code <> 'ROLE_VIEWER' OR EXISTS (
                    SELECT 1 FROM region_demo_review review
                    WHERE review.record_id = item.id AND review.status = 'APPROVED'
                ))
            )
        )
        FROM app_user actor JOIN app_role role ON role.id = actor.role_id
        JOIN raw_dataset_record item ON item.id = record_id
        WHERE actor.id = actor_id
    ), FALSE)
$$;
