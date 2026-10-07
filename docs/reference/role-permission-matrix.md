# MA TRẬN VAI TRÒ VÀ PHÂN QUYỀN TRUY CẬP (ROLE_PERMISSION_MATRIX)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0 (Giai đoạn 0 - Khảo sát và Chốt phạm vi)

---

## 1. NGUYÊN TẮC THIẾT KẾ VÀ MÔ HÌNH PHÂN QUYỀN

1. **Mô hình Phân quyền Dựa trên Vai trò (Role-Based Access Control - RBAC):** Hệ thống phân định rõ ranh giới quyền hạn theo 4 nhóm vai trò chính từ cấp độ tra cứu công khai đến quản trị toàn cục.
2. **Kiểm soát Phạm vi Dữ liệu theo Địa bàn & Đơn vị (Data Scope Restriction):** Cán bộ thuộc Khu Quản lý Đường bộ I chỉ được quyền chỉnh sửa/quản lý tài sản thuộc các tuyến đường và tỉnh thành do Khu I phụ trách (`branch_id = 1` hoặc `organization_id`). Cấp Cục Đường bộ (`admin`, `manager` toàn cục) có quyền truy cập toàn quốc.
3. **Nguyên tắc Đặc quyền Tối thiểu (Principle of Least Privilege):** Mặc định mọi quyền ghi (`create`, `update`, `delete`) đều bị từ chối trừ khi được cấp phát rõ ràng theo chức năng công tác.
4. **Bảo mật Dữ liệu Kiểm toán (Audit Immutability):** Mọi hành động làm thay đổi dữ liệu (`POST`, `PUT`, `DELETE`) bắt buộc phải ghi lại `audit_log` gồm: người thực hiện, thời gian, IP, bản ghi trước và sau khi sửa.

---

## 2. DANH MỤC 4 VAI TRÒ CHUẨN TRONG HỆ THỐNG

| Mã vai trò | Tên vai trò tiếng Việt | Đối tượng áp dụng | Phạm vi quyền hạn tổng quan |
| :---: | :--- | :--- | :--- |
| **`viewer`** | Cán bộ Tra cứu | Cán bộ các sở ngành, cơ quan nghiên cứu, công chúng được cấp tài khoản. | Chỉ đọc (Read-only): Xem Dashboard, tra cứu danh mục, tìm kiếm công trình, hiển thị bản đồ WebGIS, xem hồ sơ kỹ thuật công khai. |
| **`editor`** | Cán bộ Kỹ thuật / Nhập liệu | Chuyên viên các Chi cục QLĐB, Hạt Quản lý Đường bộ, Ban Duy tu. | Toàn bộ quyền của `viewer` + Cập nhật thông tin kỹ thuật công trình, bổ sung lý trình, đính kèm hồ sơ bản vẽ/ảnh hiện trường từ MinIO. |
| **`manager`** | Cán bộ Quản lý / Lãnh đạo | Lãnh đạo Đội/Hạt, Lãnh đạo Chi cục/Khu QLĐB, Lãnh đạo Cục. | Toàn bộ quyền của `editor` + Phê duyệt kết quả kiểm tra định kỳ, phê duyệt kế hoạch vốn bảo trì, xuất báo cáo tổng hợp cấp Cục, quản lý phân công đơn vị. |
| **`admin`** | Quản trị viên Toàn quyền | Đội ngũ Quản trị Hệ thống thuộc Trung tâm CNTT Cục Đường bộ. | Toàn quyền trên mọi phân hệ (Superuser): Quản lý tài khoản cán bộ, gán vai trò, điều phối Worker nạp dữ liệu lớn, xem Audit Log, cấu hình hệ thống. |

---

## 3. MA TRẬN PHÂN QUYỀN THEO PHÂN HỆ VÀ HÀNH ĐỘNG (MODULE & ACTION MATRIX)

*Ký hiệu:*
- $\checkmark$: Được phép thực hiện.
- $\times$: Bị từ chối (403 Forbidden).
- $[Scope]$: Được phép thực hiện nhưng chỉ trong phạm vi địa bàn / đơn vị được giao phụ trách.

| Phân hệ nghiệp vụ | Hành động (Action) | `viewer` | `editor` | `manager` | `admin` |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **1. Bảng điều hành (Dashboard)** | Xem chỉ số tổng quan toàn quốc | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Lọc số liệu theo Khu / Tỉnh thành | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| **2. Cây & Danh sách Tài sản (Assets)** | Tra cứu danh sách, xem chi tiết công trình | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Tìm kiếm mờ toàn cục theo tên / tuyến | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Thêm mới công trình tài sản | $\times$ | $[Scope]$ | $\checkmark$ | $\checkmark$ |
| | Cập nhật thông tin công trình | $\times$ | $[Scope]$ | $\checkmark$ | $\checkmark$ |
| | Xóa mềm công trình (`is_deleted = true`) | $\times$ | $\times$ | $[Scope]$ | $\checkmark$ |
| | Xuất danh sách ra file Excel / CSV | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| **3. Bản đồ số (WebGIS)** | Hiển thị các lớp chuyên đề (Point/Line) | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Truy vấn không gian theo BBOX / Bán kính | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Xem Popup lý lịch công trình trên bản đồ | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Cập nhật tọa độ / hình học PostGIS | $\times$ | $[Scope]$ | $\checkmark$ | $\checkmark$ |
| **4. Báo cáo & Thống kê (Reports)** | Xem báo cáo chiều dài đường, bảo trì | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Xuất báo cáo ra file Excel / PDF | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Phê duyệt số liệu báo cáo bảo trì định kỳ | $\times$ | $\times$ | $\checkmark$ | $\checkmark$ |
| **5. Hồ sơ & Tài liệu (Documents)** | Duyệt cây thư mục hồ sơ | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Tải tệp bản vẽ/văn bản từ MinIO (Download)| $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Tải lên tệp tài liệu mới (Upload to MinIO) | $\times$ | $[Scope]$ | $\checkmark$ | $\checkmark$ |
| | Xóa tệp tài liệu kỹ thuật | $\times$ | $\times$ | $[Scope]$ | $\checkmark$ |
| **6. Danh mục Tham chiếu (Catalogs)** | Tra cứu 152 danh mục chuẩn | $\checkmark$ | $\checkmark$ | $\checkmark$ | $\checkmark$ |
| | Chỉnh sửa / thêm mới mục danh mục | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| **7. Quản trị Nạp Dữ liệu (Import)** | Kích hoạt phiên nạp dữ liệu Import Worker | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| | Xem tiến độ nạp và đối soát số dòng | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| | Xem và xuất nhật ký lỗi `import_error` | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| | Kích hoạt Rollback phiên nạp | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| **8. Quản trị Người dùng & Phân quyền** | Thêm mới / Khóa tài khoản cán bộ | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| | Phân vai trò (`roles`) cho người dùng | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| | Reset mật khẩu người dùng | $\times$ | $\times$ | $\times$ | $\checkmark$ |
| **9. Nhật ký Kiểm toán (Audit Log)** | Tra cứu lịch sử thao tác hệ thống | $\times$ | $\times$ | $\times$ | $\checkmark$ |

---

## 4. CHÍNH SÁCH BẢO MẬT VÀ CHE DẤU THÔNG TIN (DATA MASKING)

1. **Che dấu Mật khẩu & Token:** Mật khẩu trong bảng `app_user` được băm bằng BCrypt và **không bao giờ được trả về qua API** (`@JsonIgnore` trên DTO).
2. **Bảo vệ Endpoint Quản trị:** Toàn bộ API bắt đầu bằng `/api/v1/admin/**` và `/api/v1/import/**` được Spring Security cấu hình `@PreAuthorize("hasRole('ADMIN')")`.
3. **Kiểm tra Quyền cấp Dữ liệu (Row-Level Security / Filter Scope):** Khi người dùng có vai trò `editor` thực hiện cập nhật công trình, Service layer tự động kiểm tra:
   $$\text{asset.branch\_id} == \text{currentUser.branch\_id} \quad \text{hoặc} \quad \text{currentUser.isGlobalAdmin}$$
   Nếu không khớp, hệ thống ném ngoại lệ `AccessDeniedException` (HTTP 403 Forbidden).

---

## 5. CHÍNH SÁCH TÀI KHOẢN MẪU KHỞI TẠO (IDEMPOTENT SEED DATA)

Khi khởi động ứng dụng lần đầu qua Flyway hoặc Data Seeder, hệ thống tự động khởi tạo 4 tài khoản thử nghiệm tương ứng 4 vai trò. Mọi tài khoản seed đều tuân thủ nguyên tắc an toàn:
- Mật khẩu mặc định được băm an toàn, bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên trên production.
- Script seed sử dụng `INSERT INTO app_user (...) ON CONFLICT (username) DO NOTHING` để bảo đảm tính lũy tiến (Idempotent).

| Username | Họ tên đại diện | Vai trò gán | Đơn vị mẫu | Ghi chú |
| :--- | :--- | :---: | :--- | :--- |
| `viewer_demo` | Cán bộ Tra cứu Thử nghiệm | `ROLE_VIEWER` | Cục Đường bộ Việt Nam | Chỉ có quyền đọc |
| `editor_demo` | Chuyên viên Kỹ thuật Hạt | `ROLE_EDITOR` | Chi cục QLĐB I.1 | Sửa trong phạm vi Khu I |
| `manager_demo`| Lãnh đạo Khu Quản lý ĐB I | `ROLE_MANAGER`| Khu QLĐB I | Quản lý toàn bộ Khu I |
| `admin` | Quản trị viên Hệ thống | `ROLE_ADMIN` | Cục Đường bộ Việt Nam | Toàn quyền hệ thống |
