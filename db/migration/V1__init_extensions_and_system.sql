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
