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
