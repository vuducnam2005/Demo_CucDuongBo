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
