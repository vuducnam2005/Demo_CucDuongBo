# 0003. KIẾN TRÚC FRONTEND SHELL VÀ THÀNH PHẦN GIAO DIỆN CHUNG

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Giao diện người dùng của Hệ thống Quản lý KCHT ĐB cần tái hiện luồng trải nghiệm nghiệp vụ tương tự website mẫu của Cục Đường bộ (`https://kcht.drvn.gov.vn/dashboard`), phục vụ công tác điều hành của cơ quan quản lý nhà nước nhưng tuân thủ nghiêm ngặt các quy tắc:
1. **Sử dụng tài sản hợp pháp:** Tuyệt đối không copy mã nguồn đóng hoặc tài sản không thuộc quyền sở hữu của dự án; tự xây dựng toàn bộ component từ nền tảng mã nguồn mở chuẩn mực.
2. **Quy tắc Một Thư viện UI duy nhất:** Duy nhất **Ant Design 5** (`antd` + `@ant-design/icons`) kết hợp định dạng ngôn ngữ Tiếng Việt (`vi_VN`).
3. **Kiến trúc Vỏ ứng dụng (App Shell):**
   - **Sidebar/Menu:** Điều hướng phân cấp, thu gọn/mở rộng linh hoạt, đồng bộ route đang hoạt động, lọc menu theo vai trò người dùng (RBAC).
   - **Top bar (Header):** Hiển thị nhận diện Cục Đường bộ VN, breadcrumb động, chuông thông báo, menu tài khoản cán bộ và chức năng đăng xuất.
   - **Breadcrumb động:** Tự động phản ánh cấu trúc trang từ URL hiện tại (`useLocation`).
   - **Route Guard:** Bảo vệ toàn diện các tuyến đường nội bộ, tự động chuyển hướng về `/login?redirect=...` khi chưa đăng nhập, kiểm soát quyền truy cập theo vai trò (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EDITOR`, `ROLE_VIEWER`) và hiển thị trang `403 Forbidden` khi không đủ thẩm quyền.
   - **Bố cục Responsive:** Tối ưu hiển thị cho Desktop (1920x1080), Laptop (1366x768) và Tablet công tác hiện trường.
   - **Bộ Thành phần Giao diện Chung (Common UI Components):**
     - Notification / Toast (`App.useApp()` / message / notification)
     - Confirm Modal (xác nhận an toàn cho thao tác nguy hiểm)
     - Loading Skeleton (khung xương nạp dạng Table, Card, Page)
     - Empty State (hình ảnh và thông báo rỗng thân thiện)
     - Error Boundary (bắt lỗi runtime, tránh màn hình trắng, cung cấp nút thử lại)
     - Pagination chuẩn hóa (StandardPagination thích ứng 0-indexed API và 1-indexed UI)
     - Table Toolbar (ô tìm kiếm từ khóa, bộ lọc phân đoạn, nút làm mới quay động, nút xuất báo cáo)
     - Design Tokens (Theme token tông màu Xanh Navy `#003a8c` đại diện Cục Đường bộ).
4. **Không nhúng dữ liệu lớn vào bundle frontend:** Mọi dữ liệu (658 datasets, tài sản công trình, báo cáo, log) đều phải truy vấn qua REST API (`/api/*`). Bundle ứng dụng chính chỉ xấp xỉ ~60 kB (gzipped ~19 kB).

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

### Quyết định 1: Design Tokens và Nhận diện Thương hiệu Hành chính Nhà nước
- Thiết lập bảng token giao diện trong `src/theme/themeConfig.ts`:
  - `colorPrimary`: `#003a8c` (Xanh Navy đậm chuẩn nhận diện Cục Đường bộ Việt Nam).
  - `colorSuccess`: `#52c41a`, `colorWarning`: `#fa8c16`, `colorError`: `#ff4d4f`, `colorInfo`: `#1677ff`.
  - `borderRadius`: `6px` đảm bảo tính hiện đại, trang nhã.
  - Sử dụng `<ConfigProvider locale={viVN} theme={appTheme}>` bao bọc `<AntdApp>` để toàn bộ component như DatePicker, Pagination, Table tự động bản địa hóa Tiếng Việt.

### Quyết định 2: Quản lý Trạng thái Xác thực & Tự động Làm mới Phiên (AuthContext & Interceptors)
- **Token Memory & Local Storage Cache:** Access Token được quản lý an toàn trong bộ nhớ và cache tại `localStorage` kèm hồ sơ `kcht_user_profile` để duy trì phiên khi tải lại trang.
- **Axios Interceptors:**
  - *Request Interceptor:* Tự động đính kèm `Authorization: Bearer <token>`.
  - *Response Interceptor:* Bắt mã lỗi HTTP `401 Unauthorized`, tự động gọi `POST /api/auth/refresh` bằng HttpOnly Cookie để xoay vòng token trong suốt với người dùng. Nếu refresh thất bại mới điều hướng về `/login`.
  - *Offline Resilience:* Sự cố mất kết nối mạng (Network Error) không xóa phiên đăng nhập; chỉ xóa phiên khi nhận được mã từ chối 401 rõ ràng từ server.

### Quyết định 3: Bảo vệ Định tuyến Phân cấp (ProtectedRoute & PublicOnlyRoute)
- Tuyến `/login` được bảo vệ bởi `PublicOnlyRoute`: nếu cán bộ đã đăng nhập sẽ tự động chuyển tiếp tới `/dashboard`.
- Các tuyến nghiệp vụ (`/dashboard`, `/assets`, `/map`, `/reports`, `/documents`, `/catalogs`) được bảo vệ bởi `ProtectedRoute`.
- Các tuyến quản trị cấp cao (`/admin/users`, `/admin/audit-logs`) yêu cầu bổ sung `requiredRoles={['ROLE_ADMIN']}`. Người dùng vai trò thấp hơn sẽ nhận giao diện `403 - Không có quyền truy cập`.

### Quyết định 4: Tách nhỏ Bundle và Tối ưu Nạp Tài nguyên (Code Splitting)
- Cấu hình `vite.config.ts` chia tách manualChunks:
  - `vendor-react`: React, React-DOM, React Router.
  - `vendor-antd`: Ant Design 5 & bộ icon SVG.
  - `vendor-query`: TanStack Query & Axios.
  - `vendor-ol`: Thư viện bản đồ OpenLayers.
- Bundle mã nguồn logic ứng dụng (`index-*.js`) chỉ có kích thước **60.35 kB** (nén gzip: **19.43 kB**), loại bỏ hoàn toàn nguy cơ phình to bundle do nhúng dữ liệu tĩnh.

---

## 3. CÁC ĐÁNH ĐỔI VÀ HỆ QUẢ (TRADE-OFFS & CONSEQUENCES)

| Phương án xem xét | Ưu điểm | Nhược điểm / Đánh đổi | Quyết định |
| :--- | :--- | :--- | :---: |
| **Dùng nhiều UI library (Ant Design + Tailwind + MUI)** | Tự do sáng tạo component | Xung đột CSS, phình to bundle, giao diện thiếu nhất quán | **BÁC BỎ** (Vi phạm nguyên tắc AI_RULES) |
| **Một UI library duy nhất: Ant Design 5 (Phương án chọn)** | Đầy đủ component quản trị, hỗ trợ TypeScript xuất sắc, bản địa hóa Tiếng Việt hoàn hảo, theme tokens mạnh mẽ | Cần tùy biến css để phù hợp bố cục nghiệp vụ | **ĐÃ CHỌN** |
| **Lưu Access Token vào Cookie thường** | Tự động gửi lên server | Dễ bị lộ do không có HttpOnly nếu JS bị can thiệp | **BÁC BỎ** |
| **Access Token trong Memory/Header + Refresh Cookie HttpOnly (Phương án chọn)** | Kháng XSS với refresh token, linh hoạt với SPA | Cần axios interceptor tự động refresh | **ĐÃ CHỌN** |

---

## 4. MA TRẬN KIỂM THỬ GIAO DIỆN (FRONTEND TEST MATRIX)

Đã thiết lập bộ kiểm thử tự động với Vitest + React Testing Library + JSDOM (`npm test`):
- `AuthContext.test.tsx` (3 tests): Kiểm tra khởi tạo khách, xác thực vai trò ADMIN, phân quyền hạn mức EDITOR.
- `RouteGuard.test.tsx` (3 tests): Kiểm tra chặn truy cập khi chưa đăng nhập, cho phép truy cập khi hợp lệ, chặn 403 khi thiếu quyền.
- `CommonComponents.test.tsx` (7 tests): Kiểm tra `TableToolbar`, `EmptyState`, `StandardPagination`, `TableSkeleton`, `CardSkeleton`, `ErrorBoundary`.
- **Tổng cộng: 13 tests PASSED 100%.**
