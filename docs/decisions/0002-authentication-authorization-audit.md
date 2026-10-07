# 0002. KIẾN TRÚC XÁC THỰC, PHÂN QUYỀN VÀ KIỂM TOÁN (AUTHENTICATION, AUTHORIZATION & AUDIT)

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Hệ thống Quản lý KCHT ĐB phục vụ nhiều nhóm đối tượng từ cấp lãnh đạo Cục, cán bộ các Khu Quản lý Đường bộ, chuyên viên kỹ thuật các Chi cục/Hạt, cho đến các cơ quan ban ngành tra cứu thông tin. Hệ thống đòi hỏi:
1. **Xác thực an toàn, phi trạng thái (Stateless):** Hỗ trợ kiến trúc SPA (React + TypeScript) và API Microservices trong tương lai.
2. **Phân quyền RBAC tối thiểu 4 vai trò:** `viewer`, `editor`, `manager`, `admin`.
3. **Quyền hạn gắn theo ma trận:** Phân hệ/Module (`DASHBOARD`, `ASSET`, `GIS`, `REPORT`, `DOCUMENT`, `CATALOG`, `IMPORT`, `USER`, `AUDIT`), Dataset cụ thể, và Hành động (`read`, `create`, `update`, `delete`, `export`, `manage_users`).
4. **Tương thích schema thực tế:** Tái sử dụng bảng `app_user`, `app_role`, `app_permission`, `audit_log` từ `V1__init_extensions_and_system.sql`. Không tạo hệ thống quyền thứ hai.
5. **Cơ chế token an toàn:** JWT Access Token ngắn hạn (15 phút), Refresh Token xoay vòng đơn kỳ (Single-use rotation) lưu mã băm SHA-256 trong CSDL, hỗ trợ HttpOnly Cookie chống tấn công XSS.
6. **Mật khẩu & Rate Limit:** Băm mật khẩu bằng BCrypt (strength 12), Rate Limit tối đa 5 lần đăng nhập sai trong 15 phút (trả về HTTP 429).
7. **Bảo mật tuyệt đối (Zero Credential Leakage):** Tuyệt đối không ghi log mật khẩu, token hoặc secret; tự động thanh lọc (masking) payload trong `audit_log`.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

### Quyết định 1: Mô hình Token Kép (Dual-Token Architecture with Rotation)
- **Access Token:** Ký HMAC-SHA256, thời hạn 15 phút (900 giây). Payload chứa: `userId`, `username`, `role`, `permissions`, `branchId`, `organizationId`. Cho phép API Gateway hoặc backend service xác thực ngay lập tức mà không cần truy vấn CSDL liên tục.
- **Refresh Token:** Chuỗi ngẫu nhiên 32 bytes (64 ký tự hex) sinh từ `SecureRandom`, có thời hạn 7 ngày. Khi lưu vào database (`app_refresh_token`), chỉ lưu **mã băm SHA-256** (`token_hash`) để phòng ngừa nguy cơ rò rỉ CSDL.
- **Xoay vòng đơn kỳ (Single-use Rotation):** Mỗi lần gọi `/api/auth/refresh`, refresh token cũ bị đánh dấu `revoked = true` và một token mới được cấp phát. Nếu phát hiện token đã thu hồi bị sử dụng lại (dấu hiệu token bị đánh cắp), hệ thống lập tức thu hồi toàn bộ token của tài khoản đó.
- **Truyền nhận qua HttpOnly Cookie:** Server gửi kèm cookie `kcht_refresh_token` với cờ `HttpOnly`, `SameSite=Lax`, `Path=/api/auth` để trình duyệt tự động bảo vệ khỏi JavaScript độc hại.

### Quyết định 2: Tái sử dụng Schema Hiện có và Migration V6 Tương thích
- Sử dụng trực tiếp các bảng `app_role`, `app_user`, `app_permission` và `audit_log` từ migration V1.
- Bổ sung migration `V6__seed_and_auth.sql`:
  - Tạo bảng `app_refresh_token` (liên kết khóa ngoại tới `app_user(id)`).
  - Seed 4 vai trò chuẩn: `ROLE_VIEWER`, `ROLE_EDITOR`, `ROLE_MANAGER`, `ROLE_ADMIN`.
  - Seed 58 quyền hạn chi tiết gắn theo module và action: `read`, `create`, `update`, `delete`, `export`, `manage_users`.
  - Seed 4 tài khoản mẫu thử nghiệm idempotent bằng `ON CONFLICT DO UPDATE`.

### Quyết định 3: Bộ chặn Tấn công Dò Mật khẩu (Login Rate Limiter)
- Triển khai `LoginRateLimiter` quản lý theo cửa sổ trượt (sliding window) theo dõi cả IP khách và username.
- Khi vượt quá 5 lần đăng nhập thất bại, hệ thống khóa truy cập trong 15 phút và trả về mã lỗi HTTP `429 Too Many Requests` tuân thủ chuẩn RFC 7807.
- Xóa bộ đếm ngay khi người dùng đăng nhập thành công.

### Quyết định 4: Cơ chế Kiểm toán Độc lập và Khử bỏ Dữ liệu Nhạy cảm (Audit Sanitization)
- `AuditLogService` ghi nhận toàn bộ sự kiện: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `TOKEN_REFRESH`, `CREATE_USER`.
- Mọi dữ liệu JSON trước khi lưu vào cột `new_values`/`old_values` (JSONB) đều đi qua bộ lọc `removeSensitiveFields` để loại bỏ vĩnh viễn các trường: `password`, `passwordHash`, `token`, `accessToken`, `refreshToken`, `secret`, `credential`.
- Tuyệt đối không log thông tin nhạy cảm ra file log server hoặc console.

---

## 3. CÁC ĐÁNH ĐỔI VÀ HỆ QUẢ (TRADE-OFFS & CONSEQUENCES)

| Phương án xem xét | Ưu điểm | Nhược điểm / Đánh đổi | Quyết định |
| :--- | :--- | :--- | :---: |
| **JWT Stateless hoàn toàn (Không lưu Refresh Token)** | Không cần I/O database khi refresh. | Không thể thu hồi token khi cán bộ nghỉ việc hoặc bị lộ tài khoản. | **BÁC BỎ** (Vi phạm an toàn thông tin) |
| **Session Cookie Stateful (Spring Session + Redis)** | Kiểm soát phiên tức thời, dễ vô hiệu hóa. | Phụ thuộc Redis cluster, khó mở rộng cho mobile app/GIS client. | **BÁC BỎ** cho giai đoạn hiện tại |
| **JWT Access Token ngắn hạn + Refresh Token có DB state (Phương án chọn)** | Vừa tối ưu throughput API, vừa thu hồi được phiên khi cần thiết, chống tấn công đánh cắp token qua xoay vòng đơn kỳ. | Tốn 1 truy vấn database khi thực hiện refresh token (mỗi 15 phút một lần). | **ĐÃ CHỌN** |
| **Tích hợp SSO ngay (Keycloak/OAuth2)** | Tập trung hóa quản trị người dùng. | Cần hạ tầng IdP riêng, phức tạp hóa việc phát triển cục bộ và smoke test. | **ĐỂ DÀNH** cho giai đoạn sau |

---

## 4. MA TRẬN 4 VAI TRÒ VÀ TÀI KHOẢN MẪU KHỞI TẠO

| Username | Mật khẩu mặc định | Vai trò (Role) | Đơn vị mẫu | Quyền hạn chính |
| :--- | :--- | :---: | :--- | :--- |
| `viewer_demo` | credential test được quản lý ngoài tài liệu | `ROLE_VIEWER` | Cục Đường bộ VN | Chỉ đọc: Dashboard, Tài sản, WebGIS, Báo cáo, Hồ sơ kỹ thuật, Danh mục. |
| `editor_demo` | credential test được quản lý ngoài tài liệu | `ROLE_EDITOR` | Chi cục QLĐB I.1 | Toàn bộ quyền viewer + Thêm mới/Sửa công trình, upload hồ sơ bản vẽ. |
| `manager_demo`| credential test được quản lý ngoài tài liệu | `ROLE_MANAGER`| Khu QLĐB I | Toàn bộ quyền editor + Xóa công trình, xem Audit Log, phê duyệt báo cáo. |
| `admin` | credential test được quản lý ngoài tài liệu | `ROLE_ADMIN` | Cục Đường bộ VN | Toàn quyền Superuser: Quản trị tài khoản, gán quyền, xem Audit Log, cấu hình. |
