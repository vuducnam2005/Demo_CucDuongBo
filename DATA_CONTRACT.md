# QUY ƯỚC DỮ LIỆU VÀ HỢP ĐỒNG BÀN GIAO DATABASE - CODE (DATA_CONTRACT)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản Hợp đồng:** 1.0.0  

---

## 1. NGUỒN SỰ THẬT VÀ NGUYÊN TẮC BÀN GIAO (SINGLE SOURCE OF TRUTH)

Sau khi hoàn thành các prompt database, toàn bộ tài liệu đặc tả, migration thực tế và báo cáo đối soát trong thư mục `docs/database/` và `db/migration/` là **Nguồn sự thật duy nhất (Single Source of Truth)**. Coding Agent và kỹ sư bắt buộc phải đọc và đối chiếu tối thiểu các tài liệu sau trước khi viết bất kỳ dòng mã truy cập dữ liệu nào:

1. [`docs/database/DATABASE_ARCHITECTURE.md`](file:///c:/Demo_CucDuongBo/docs/database/DATABASE_ARCHITECTURE.md): Kiến trúc hai tầng Raw Ingestion Lake và Curated ODS.
2. [`docs/database/DATA_MODEL.md`](file:///c:/Demo_CucDuongBo/docs/database/DATA_MODEL.md): Mô hình dữ liệu quan hệ và thuộc tính mở rộng JSONB.
3. [`docs/database/RAW_SCHEMA.md`](file:///c:/Demo_CucDuongBo/docs/database/RAW_SCHEMA.md): Đặc tả cấu trúc bảng `raw_dataset_record`, `dataset_registry`, `dataset_field`.
4. [`docs/database/CURATED_SCHEMA.md`](file:///c:/Demo_CucDuongBo/docs/database/CURATED_SCHEMA.md): Đặc tả cấu trúc bảng `asset_record`, `asset_geometry`, liên kết quan hệ.
5. [`docs/database/KEYS_AND_INDEXES.md`](file:///c:/Demo_CucDuongBo/docs/database/KEYS_AND_INDEXES.md): Chiến lược khóa tự nhiên, khóa thay thế, chỉ mục GiST/GIN/B-Tree.
6. [`docs/database/GEOMETRY_POLICY.md`](file:///c:/Demo_CucDuongBo/docs/database/GEOMETRY_POLICY.md): Quy chuẩn không gian PostGIS, hệ quy chiếu EPSG:4326 và độ mịn hình học.
7. [`docs/database/IMPORT_RUNBOOK.md`](file:///c:/Demo_CucDuongBo/docs/database/IMPORT_RUNBOOK.md): Sổ tay vận hành nạp dữ liệu, kiểm tra đối soát và xử lý sự cố.
8. **Toàn bộ Flyway Migration** (`db/migration/V1` đến `V5`) và **Báo cáo đối soát** ([`FULL_IMPORT_REPORT.md`](file:///c:/Demo_CucDuongBo/docs/database/FULL_IMPORT_REPORT.md)).

> [!CRITICAL]
> **Nguyên tắc Giải quyết Mâu thuẫn:** Nếu phát hiện bất kỳ mâu thuẫn nào giữa yêu cầu prompt và schema/migration đã chạy thực tế trong database, **phải dừng ngay phần phụ thuộc**, ghi chép chi tiết vào [`OPEN_QUESTIONS.md`](file:///c:/Demo_CucDuongBo/OPEN_QUESTIONS.md) và báo cáo cho người dùng. **Tuyệt đối không tự ý DROP bảng, sửa dữ liệu đã nạp hoặc tạo schema song song.**

---

## 2. 6 ĐIỂM BẮT BUỘC PHẢI CHỐT TRƯỚC KHI VIẾT API

### Điểm 1: Ánh xạ Định danh Dữ liệu Thống nhất
- **Quy tắc:**
  - Định danh tập dữ liệu: `dataset_key` (hoặc `dataset_id`), ví dụ: `tbl_bridge`, `mst_national_road`, `duonggom`.
  - Định danh bản ghi tài sản: `record_key` (hoặc `asset_id`), ví dụ: `bridge_45228`, `guardrail_521041`.
  - Định danh phiên nạp: `import_job_id` (ví dụ `IMPORT_JOB_1791215586055`).
- **Ràng buộc:** Giữ nguyên các định danh đã chốt trong database, tuyệt đối không tự ý đổi tên, tạo định danh thứ hai hoặc tự sinh ID song song gây sai lệch đối soát.

### Điểm 2: Nguồn Đọc cho Từng API & Cách ly Bảng Raw
- **Quy tắc:**
  - **Asset API:** Đọc từ `asset_record` (thuộc tính định danh, lý trình, hành chính, JSONB attributes).
  - **Spatial / Map API:** Đọc từ `asset_geometry` (PostGIS geometry SRID 4326) kết hợp `asset_record` (properties).
  - **Catalog API:** Đọc từ `reference_catalog`.
  - **Document API:** Đọc từ `document_folder` và `document_metadata`.
  - **Ingestion / Registry API:** Đọc từ `dataset_registry`, `dataset_field`.
- **Ràng buộc Bảo mật:** **Tuyệt đối không cho frontend truy cập trực tiếp vào `raw_dataset_record`**. Bảng raw chỉ phục vụ ingestion, audit trail và replay.

### Điểm 3: Quy chuẩn Không gian PostGIS và Thứ tự Tọa độ
- **Hệ quy chiếu:** **WGS 84 (EPSG:4326)**.
- **Thứ tự Tọa độ:** Bắt buộc tuân theo chuẩn GIS quốc tế: **`(Longitude, Latitude)`** hay **`(Kinh độ / X, Vĩ độ / Y)`**.
- **Chuyển đổi GeoJSON:** API Map sử dụng hàm PostGIS `ST_AsGeoJSON` hoặc Jackson GeoJsonModule để trả về chuẩn RFC 7946 `FeatureCollection`. Thống nhất đồng bộ giữa API Map và PostGIS.

### Điểm 4: Tính Nhất quán và Tương thích Ngược của API Contract
- Các endpoint phải dùng đúng tên route, kiểu response, pagination, filter/sort allowlist và error contract đã được xây dựng từ Prompt 8 của tài liệu database:
  - `GET /api/datasets`: Danh sách dataset và metadata
  - `GET /api/datasets/{datasetKey}/metadata`: Từ điển trường dữ liệu
  - `GET /api/datasets/{datasetKey}/records`: Danh sách bản ghi có phân trang, search, filter
  - `GET /api/datasets/{datasetKey}/records/{recordKey}`: Chi tiết một bản ghi
  - `GET /api/datasets/{datasetKey}/geo`: Xuất dữ liệu không gian GeoJSON
  - `GET /api/reference-catalogs`: Danh mục tham chiếu
  - `GET /api/documents`: Cây thư mục và danh sách file tài liệu
- Phân trang bắt buộc: `page` (0-indexed), `size` (mặc định 20, tối đa 100). Sắp xếp ổn định (`ORDER BY id/created_at`) để không bị lặp hoặc mất bản ghi khi chuyển trang.

### Điểm 5: Ánh xạ Vai trò và Phân quyền Người dùng (RBAC)
- Bảng nguồn: `app_user`, `app_role`, `app_permission`.
- Danh sách Vai trò Chuẩn:
  1. `viewer`: Chỉ xem bản đồ, tra cứu danh mục, hồ sơ kỹ thuật.
  2. `editor`: Cập nhật lý trình, chỉnh sửa thông tin tài sản, đính kèm hồ sơ.
  3. `manager`: Phê duyệt trạng thái bảo trì, kiểm tra báo cáo, quản lý đơn vị.
  4. `admin`: Toàn quyền hệ thống, quản lý tài khoản, điều phối nạp dữ liệu.
- **Ràng buộc:** Dữ liệu seed tài khoản ban đầu phải **Idempotent** (`INSERT ... ON CONFLICT DO NOTHING`) và **tuyệt đối không chứa mật khẩu thật**.

### Điểm 6: Cơ chế Quản lý Tài liệu Nhị phân (Binary Files & Storage)
- **Cơ sở dữ liệu:** Chỉ lưu trữ metadata (`file_id`, `original_name`, `mime_type`, `bytes`, `sha256`, `object_key`, `asset_id`).
- **Lưu trữ Tệp Nhị phân:** Sử dụng Adapter **MinIO / S3** ở giai đoạn tài liệu.
- **Ràng buộc:** Tuyệt đối không nhét tệp nhị phân lớn vào PostgreSQL dưới dạng `BYTEA` và không bundle tệp tài liệu tĩnh vào gói build frontend.

---

## 3. CÁC QUY ƯỚC DỮ LIỆU ĐÃ ĐƯỢC CHỐT

### 3.1 Phân loại Dataset Nguồn
- **Physical Assets (57 tệp):** `assets/*.json` (~833,000 bản ghi). Nạp vào `raw_dataset_record`, chuẩn hóa vào `asset_record` và `asset_geometry`.
- **Reference Catalogs (152 tệp):** `modules/*.json` (mst, cat) (~6,400 bản ghi). Nạp vào `reference_catalog`.
- **Documents & Folders (123 tệp):** `document_files_index.json` và metadata (309 files, 121 folders). Nạp vào `document_folder` và `document_metadata`.
- **Maintenance Reports (314 tệp):** `modules/maintenance_*.json` (~264,000 bản ghi). Lưu 100% tại `raw_dataset_record` phục vụ kiểm toán; phục vụ báo cáo qua Dynamic SQL Views.

### 3.2 Bộ lọc Tọa độ Lãnh thổ Việt Nam
$$102.0^\circ \le X \le 112.0^\circ \quad \text{và} \quad 8.0^\circ \le Y \le 24.0^\circ$$
Tọa độ ngoài khung hoặc $(0, 0)$ được đánh dấu `is_valid_geometry = FALSE` và không nạp vào bản đồ.

### 3.3 Giao thức Đối soát Số dòng (Row Count Reconciliation)
$$\text{manifest\_records} = \text{records\_inserted} + \text{records\_updated} + \text{records\_skipped} + \text{records\_failed}$$
Toàn bộ quá trình nạp phải được ghi nhận tiến độ vào `import_job` và `import_file`, cho phép resume và đối soát chính xác tới từng dòng.
