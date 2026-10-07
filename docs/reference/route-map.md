# BẢN ĐỒ ĐIỀU HƯỚNG VÀ TUYẾN ĐƯỜNG DẪN FRONTEND (ROUTE_MAP)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0 (Giai đoạn 0 - Khảo sát và Chốt phạm vi)

---

## 1. NGUYÊN TẮC THIẾT KẾ ĐỊNH TUYẾN (ROUTING PRINCIPLES)

1. **Chuẩn hóa URL:** Toàn bộ đường dẫn URL sử dụng chữ thường, phân tách bằng dấu gạch ngang (`kebab-case`).
2. **Bảo vệ Định tuyến (Route Guards):** Toàn bộ các route bên trong hệ thống (ngoại trừ `/login`) đều yêu cầu trạng thái đã xác thực (Authenticated). Nếu chưa đăng nhập, tự động chuyển hướng về `/login?redirect={currentPath}`.
3. **Phân quyền theo Vai trò (Role-based Route Access):** Các route quản trị hệ thống (`/admin/**`) được kiểm soát chặt chẽ bởi Role Guard (`ROLE_ADMIN`).
4. **Đồng bộ Trạng thái qua URL Query Params:** Các tiêu chí tìm kiếm, lọc, số trang và cột sắp xếp bắt buộc phải được đẩy lên URL (`?page=1&size=20&q=...&routeCode=...`) để người dùng có thể chia sẻ liên kết (Bookmarkable / Sharable URLs) hoặc nhấn nút Back/Forward trên trình duyệt mà không mất trạng thái.

---

## 2. BẢNG DANH MỤC CÁC ROUTE TRONG HỆ THỐNG

| Route Path | Tên trang / Component | Tiêu đề Trang (Page Title) | Layout | Vai trò yêu cầu (Role Guard) | Breadcrumb Trail | API Backend tương ứng |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/login` | `LoginPage` | Đăng nhập hệ thống | `AuthLayout` | Công khai (Public) | Trang chủ > Đăng nhập | `POST /api/auth/login` |
| `/` | `Redirect` | Chuyển hướng | - | Authenticated | - | Chuyển hướng về `/dashboard` |
| `/dashboard` | `DashboardPage` | Bảng điều hành tổng quan | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Bảng điều hành | `GET /api/dashboard/summary`<br>`GET /api/dashboard/stats/*` |
| `/assets` | `AssetListPage` | Danh mục tài sản hạ tầng | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Danh mục tài sản | `GET /api/datasets/tree`<br>`GET /api/datasets/{key}/metadata`<br>`GET /api/datasets/{key}/records` |
| `/assets?datasetKey=...` | `AssetListPage` | Danh sách công trình theo dataset | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Tài sản > [Tên Dataset] | `GET /api/datasets/{key}/metadata`<br>`GET /api/datasets/{key}/records` |
| `/map` | `WebGisPage` | Bản đồ số kết cấu hạ tầng | `FullscreenMapLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Bản đồ WebGIS | `GET /api/datasets/{key}/geo` hoặc `GET /api/datasets/{key}/clusters` |
| `/reports` | `ReportIndexPage` | Báo cáo & Thống kê | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Báo cáo | `GET /api/reports/road-lengths`<br>`GET /api/reports/maintenance`<br>`GET /api/reports/road-signs-blackspots` |
| `/documents` | `DocumentExplorerPage` | Quản lý hồ sơ & tài liệu | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Hồ sơ tài liệu | `GET /api/documents/folders`<br>`GET /api/documents` |
| `/catalogs` | `CatalogListPage` | Danh mục chuẩn ngành | `DefaultLayout` | `viewer`, `editor`, `manager`, `admin` | Trang chủ > Danh mục tham chiếu | `GET /api/catalogs`<br>`GET /api/catalogs/{code}` |
| `/admin/users` | `UserManagementPage` | Quản lý người dùng & phân quyền | `DefaultLayout` | `admin` | Trang chủ > Quản trị > Người dùng | `GET /api/auth/users`<br>`POST /api/auth/users` |
| `/admin/audit-logs` | `AuditLogPage` | Nhật ký kiểm toán hệ thống | `DefaultLayout` | `admin` | Trang chủ > Quản trị > Nhật ký kiểm toán | `GET /api/audit-logs` |
| `*` (404) | `NotFoundPage` | Không tìm thấy trang | `DefaultLayout` | Công khai | Trang chủ > 404 | Không |

---

## 3. CÁC KHUNG GIAO DIỆN CHÍNH (LAYOUT ARCHITECTURE)

### 3.1 Khung Mặc định (`DefaultLayout`)
Áp dụng cho hơn 90% màn hình trong hệ thống (Dashboard, Danh sách tài sản, Báo cáo, Hồ sơ, Quản trị):
- **Cột bên trái (Sidebar Navigation):**
  - Logo Cục Đường bộ Việt Nam và Tên hệ thống KCHT ĐB.
  - Menu điều hướng phân cấp (Dashboard, Cây tài sản, Bản đồ, Báo cáo, Hồ sơ, Danh mục, Quản trị).
  - Thu gọn/mở rộng sidebar (Collapsible Sidebar).
- **Thanh tiêu đề phía trên (Top Header):**
  - Nút ẩn/hiện Sidebar.
  - Ô tìm kiếm nhanh toàn cục (Global Search).
  - Thông báo hệ thống (Notification Bell).
  - Khối thông tin cá nhân cán bộ: Avatar, Họ tên, Vai trò hiện tại, Đơn vị công tác.
  - Dropdown Menu: "Đổi mật khẩu", "Hồ sơ cá nhân", "Đăng xuất".
- **Khu vực nội dung chính (Main Content Area):**
  - Đường dẫn điều hướng (Breadcrumb trail).
  - Vùng render component trang con với thanh cuộn độc lập.

### 3.2 Khung Bản đồ Toàn màn hình (`FullscreenMapLayout`)
Áp dụng chuyên biệt cho màn hình `/map`:
- Tối đa hóa diện tích hiển thị của Canvas bản đồ WebGIS (100% viewport width và height).
- Header tinh gọn nổi (Floating Header) chứa logo và thanh tìm kiếm công trình nhanh.
- Hộp công cụ nổi bên trái (Floating Layer Control) cho phép bật/tắt các lớp chuyên đề (Cầu, Biển báo, Hộ lan, Rãnh biên).
- Bảng điều khiển thuộc tính trượt (Slide-over Property Drawer) bên phải khi bấm vào đối tượng công trình.

### 3.3 Khung Xác thực (`AuthLayout`)
Áp dụng cho trang `/login`:
- Bố cục căn giữa màn hình hoặc chia đôi (Split-screen):
  - Nửa bên trái: Hình ảnh hạ tầng giao thông đường bộ Việt Nam tiêu biểu (Cầu Bãi Cháy, Cao tốc Bắc - Nam).
  - Nửa bên phải: Khối form đăng nhập gồm Username, Password, Remember Me, Nút Đăng nhập và Bản quyền Cục Đường bộ.

---

## 4. MA TRẬN ĐIỀU HƯỚNG VÀ LUỒNG CHUYỂN TRANG (NAVIGATION TRANSITION FLOWS)

```
[Đăng nhập: /login]
         |
         v
[Bảng điều hành: /dashboard]
         |
         +---> [Cây tài sản: /assets] ---> [/assets/:datasetKey] ---> [/assets/:datasetKey/:recordKey]
         |                                                                      |
         |                                                      [Nút Xem trên bản đồ]
         |                                                                      v
         +---> [Bản đồ WebGIS: /map] <------------------------------------------+
         |
         +---> [Báo cáo & Thống kê: /reports] ---> [/reports/road-length] | [/reports/maintenance]
         |
         +---> [Hồ sơ & Tài liệu: /documents] ---> [/documents/folder/:folderId] ---> [Tải MinIO S3]
         |
         +---> [Danh mục chuẩn: /catalogs] ---> [/catalogs/:catalogCode]
         |
         `---> [Quản trị: /admin/users] | [/admin/audit-logs] (Chỉ Admin)
```

### Các Luồng Chuyển Trang Phổ biến:
1. **Từ Dashboard sang Danh sách Lọc:**
   - Người dùng click vào thẻ "11,631 Cầu quốc lộ" trên Dashboard -> Chuyển hướng sang `/assets?datasetKey=tbl_bridge`.
   - Click vào biểu đồ "Cầu hư hỏng" -> Chuyển hướng sang `/assets?datasetKey=tbl_bridge&state=DAMAGED`.
2. **Từ Bảng Danh sách sang Bản đồ WebGIS:**
   - Trong bảng dữ liệu công trình, click vào biểu tượng bản đồ ở cột hành động -> Chuyển hướng sang `/map?dataset=tbl_bridge&recordKey=bridge_45228` -> Bản đồ tự động bay tới vị trí tọa độ của cầu và mở Popup chi tiết.
3. **Từ Chi tiết Công trình sang Hồ sơ Tài liệu:**
   - Trong màn hình `/assets?datasetKey=tbl_bridge`, chọn bản ghi cầu -> Click vào file bản vẽ thiết kế -> Gọi API sinh Pre-signed URL và tự động tải file từ MinIO.
