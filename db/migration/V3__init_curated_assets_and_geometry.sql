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
