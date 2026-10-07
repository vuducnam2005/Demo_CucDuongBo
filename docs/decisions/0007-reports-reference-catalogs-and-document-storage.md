# 0007. BÁO CÁO TỔNG HỢP, DANH MỤC THAM CHIẾU VÀ LƯU TRỮ HỒ SƠ TÀI LIỆU KỸ THUẬT

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Hạ tầng giao thông đường bộ Việt Nam đòi hỏi các báo cáo phân tích số liệu kỹ thuật đa chiều, quản lý bảng mã danh mục chuẩn hóa dùng chung và lưu trữ tài liệu hồ sơ hoàn công công trình:

1. **Báo cáo Chiều dài Mạng lưới Tuyến Quốc lộ (`/api/reports/road-lengths`):**
   - Tổng hợp số lượng tuyến, số đoạn tuyến, tổng chiều dài mạng lưới đường bộ (km), chiều dài trung bình và tuyến quốc lộ dài nhất.
   - Phân tích sâu cấu trúc thuộc tính `data_` từ tập dữ liệu `mst_national_road`.
   - Phân bổ theo Khu vực quản lý đường bộ (Khu QLĐB I, II, III, IV) và theo Loại kết cấu mặt đường (Bê tông nhựa cấp cao A1, Bê tông nhựa thảm, Bê tông xi măng, v.v.).
2. **Báo cáo Kế hoạch & Thực hiện Bảo trì KCHT ĐB (`/api/reports/maintenance`):**
   - Tổng hợp danh mục các dự án/công trình sửa chữa định kỳ, bảo trì thường xuyên và xử lý đột xuất.
   - Thống kê tổng mức kinh phí (VNĐ), phân bổ kinh phí theo đơn vị quản lý và theo năm kế hoạch (2025, 2026), tính toán tỷ lệ hoàn thành tiến độ.
3. **Thống kê Biển báo QCVN 41 & Điểm đen TNGT (`/api/reports/road-signs-blackspots`):**
   - Phân loại 222.112 biển báo hiệu theo 5 nhóm quy chuẩn chuẩn quốc gia QCVN 41:2019/BGTVT (Biển cấm `P`, Biển cảnh báo & nguy hiểm `W`, Biển hiệu lệnh `R`, Biển chỉ dẫn `I`, Biển phụ `S`).
   - Danh sách theo dõi và kiểm soát các Điểm đen và Điểm tiềm ẩn tai nạn giao thông trọng điểm toàn quốc (lý trình Km, số vụ TNGT, số thương vong, trạng thái xử lý/khắc phục kỹ thuật).
4. **Xuất khẩu Dữ liệu CSV Chuẩn Quốc tế với UTF-8 BOM:**
   - Hỗ trợ xuất khẩu dữ liệu sang định dạng CSV. Để đảm bảo người dùng mở bằng Microsoft Excel tại Việt Nam không bị lỗi font chữ có dấu (diacritics), tất cả stream CSV đều chèn byte ký tự UTF-8 BOM (`\uFEFF`) ở đầu tệp.
5. **Danh mục Tham chiếu Chuẩn hóa (Reference Catalogs):**
   - Tra cứu tổng hợp 63 tỉnh/thành phố, cấp kỹ thuật đường bộ, loại mặt đường, quy chuẩn biển báo.
   - Hỗ trợ truy vấn linh hoạt từ cả bảng chuẩn hóa `reference_catalog` và tầng `raw_dataset_record`.
   - Kiểm soát quyền ghi (RBAC): Chỉ người dùng có vai trò `ADMIN` hoặc `MANAGER` mới được quyền thêm, sửa, xóa phần tử danh mục; người dùng `VIEWER` bị chặn `403 Forbidden`.
6. **Quản lý Hồ sơ & Tài liệu Kỹ thuật (Document Explorer & Storage):**
   - Tổ chức theo cây thư mục phân cấp (`document_folder`).
   - Tách biệt lưu trữ tệp nhị phân: Tệp tin lưu trữ an toàn trên Object Storage (MinIO / S3 / LocalStorageService), CSDL chỉ lưu siêu dữ liệu (`document_metadata`) và object key.
   - Kiểm soát an toàn tệp: Giới hạn kích thước tối đa 25MB, kiểm tra danh mục định dạng hợp lệ (PDF, DOCX, XLSX, DWG, PNG, JPG, ZIP), tính toán mã băm SHA-256 để kiểm tra tính toàn vẹn.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

```
+---------------------------------------------------------------------------------------------------+
|                                          FRONTEND (REACT + VITE)                                   |
|  - ReportIndexPage: 3 Tabs (Road Lengths, Maintenance, Road Signs & Blackspots)                   |
|    ├── KPI Metric Cards & Distribution Progress Bars                                              |
|    ├── Dynamic Filters (Branch, Route, Surface, Year, Severity)                                   |
|    └── One-click CSV Export (UTF-8 BOM compatible with Excel)                                     |
|  - CatalogListPage: Split layout (Left: Catalog groups tree; Right: Item CRUD table)              |
|    └── RBAC permission guards (Lock buttons for VIEWER, Modal CRUD for ADMIN/MANAGER)             |
|  - DocumentExplorerPage: Folder hierarchy tree & Object storage file management                   |
|    ├── Drag-and-drop file upload with 25MB limit check & SHA-256 metadata                         |
|    └── File download blob stream & RBAC-protected soft delete                                     |
+-------------------------------------------------+-------------------------------------------------+
                                                  | REST API (/api/reports/*, /api/catalogs/*, /api/documents/*)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                          BACKEND (SPRING BOOT 3.3.4)                              |
|  - ReportApiController / ReportService: Server-side aggregation & CSV stream generator             |
|  - ReferenceCatalogApiController / ReferenceCatalogQueryService: Dual-layer catalog query & RBAC  |
|  - DocumentApiController / DocumentQueryService: Multipart upload, SHA-256 hashing, MinIO proxy   |
+-------------------------------------------------+-------------------------------------------------+
                                                  | JPA / JdbcTemplate / StorageService
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                           DATABASE (POSTGRESQL 16) & STORAGE (MINIO / S3)                         |
|  - reference_catalog (Normalized catalog items & extra JSON attributes)                           |
|  - document_folder & document_metadata (Folders & Document metadata index with SHA-256)           |
|  - StorageService / MinIO Bucket 'kcht-documents' (Binary object payload)                         |
+---------------------------------------------------------------------------------------------------+
```

### 2.1. Chi tiết API Báo cáo & Xuất CSV UTF-8 BOM
- `GET /api/reports/road-lengths`: Trả về DTO tổng hợp chỉ số, phân bổ khu vực, phân bổ mặt đường và danh sách tuyến chi tiết.
- `GET /api/reports/road-lengths/export`: Trả về `text/csv; charset=UTF-8` kèm UTF-8 BOM (`\uFEFF`).
- `GET /api/reports/maintenance`: Báo cáo kinh phí, phân bổ theo năm và trạng thái thực hiện bảo trì.
- `GET /api/reports/maintenance/export`: Xuất CSV danh mục công trình bảo trì.
- `GET /api/reports/road-signs-blackspots`: Thống kê biển báo theo QCVN 41 và danh sách điểm đen.
- `GET /api/reports/road-signs-blackspots/export`: Xuất CSV dữ liệu điểm đen và an toàn giao thông.

### 2.2. Chi tiết API Danh mục Tham chiếu
- `GET /api/catalogs`: Danh sách tổng hợp toàn bộ các bảng danh mục tham chiếu hệ thống.
- `GET /api/catalogs/{catalogCode}`: Phân trang danh sách phần tử kèm tìm kiếm theo từ khóa.
- `GET /api/catalogs/{catalogCode}/{itemCode}`: Tra cứu chi tiết một phần tử.
- `POST /api/catalogs/{catalogCode}`: Thêm mới hoặc cập nhật phần tử (yêu cầu `ADMIN` hoặc `MANAGER`).
- `PUT /api/catalogs/{catalogCode}/{itemCode}`: Cập nhật phần tử (yêu cầu `ADMIN` hoặc `MANAGER`).
- `DELETE /api/catalogs/{catalogCode}/{itemCode}`: Xóa phần tử (yêu cầu `ADMIN` hoặc `MANAGER`).

### 2.3. Chi tiết API Hồ sơ Tài liệu & Object Storage
- `GET /api/documents/folders`: Danh sách cây thư mục hồ sơ.
- `POST /api/documents/folders`: Tạo thư mục mới (yêu cầu `ADMIN` hoặc `MANAGER`).
- `GET /api/documents`: Phân trang danh sách tài liệu theo thư mục hoặc từ khóa tìm kiếm.
- `GET /api/documents/{id}`: Chi tiết metadata tài liệu.
- `GET /api/documents/{id}/file`: Tải tệp tin nhị phân từ Object Storage.
- `POST /api/documents/upload`: Tải lên tài liệu mới (Multipart max 25MB, băm SHA-256, ghi vào MinIO/S3 và metadata CSDL).
- `DELETE /api/documents/{id}`: Xóa tài liệu khỏi Storage và soft-delete CSDL (yêu cầu `ADMIN` hoặc `MANAGER`).

---

## 3. HẬU QUẢ VÀ ĐÁNH GIÁ (CONSEQUENCES)

### Điểm tích cực (Pros):
1. **Tính độc lập & an toàn dữ liệu:** Tách biệt hoàn toàn tệp nhị phân sang Object Storage (MinIO/S3) giúp database PostgreSQL luôn gọn nhẹ, tối ưu hóa backup/restore và truy vấn metadata.
2. **Khả năng tương thích người dùng Việt Nam:** Byte order mark UTF-8 BOM giải quyết dứt điểm vấn đề font chữ tiếng Việt khi người dùng xuất và mở file báo cáo bằng Microsoft Excel.
3. **Phân quyền chặt chẽ (RBAC):** Cán bộ xem thông tin (`VIEWER`) được bảo vệ không thể thay đổi dữ liệu cấu hình danh mục hoặc xóa tệp hồ sơ công trình, trong khi Quản trị viên (`ADMIN`) và Quản lý (`MANAGER`) thao tác thuận tiện qua giao diện trực quan.
4. **Hỗ trợ tra cứu đa nguồn:** Kiến trúc linh hoạt cho phép hệ thống vừa truy vấn các danh mục thô đã crawl từ hệ thống cũ, vừa hỗ trợ danh mục chuẩn hóa quản lý tập trung.

---

## 4. KẾT QUẢ KIỂM THỬ (TESTING & VERIFICATION)

- **Backend Integration Tests:** 100% tests pass (9/9 tests trong `Phase10IntegrationTest`, 14/14 tests trong contract & phase 10).
- **Frontend Unit & Integration Tests:** 100% tests pass (35/35 tests trong Vitest bao gồm `Phase10Pages.test.tsx`).
- **Giao diện người dùng:** Đáp ứng đầy đủ tiêu chuẩn UX/UI của Ant Design, hiển thị tiếng Việt chuẩn hóa, responsive và phân quyền bảo mật.
