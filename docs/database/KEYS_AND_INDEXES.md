# CHIẾN LƯỢC KHÓA VÀ CHỈ MỤC TOÀN HỆ THỐNG (KEYS_AND_INDEXES)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Tổng quan Kiến trúc Khóa (Key Architecture)

Hệ thống quản lý hơn 1.1 triệu bản ghi thô và hơn 833,000 bản ghi tài sản vận hành. Để đảm bảo tính toàn vẹn dữ liệu, hiệu năng truy vấn phân trang dưới $50\text{ms}$ và khả năng liên kết vết (Data Lineage) xuyên suốt giữa các tầng, kiến trúc khóa được phân định theo 3 cấp độ:

1. **Khóa chính Kỹ thuật (Surrogate Primary Keys):** Sử dụng `BIGSERIAL` (64-bit integer, dải giá trị tới $9 \times 10^{18}$) cho tất cả các bảng. Khóa số nguyên tối ưu hóa bộ nhớ đệm B-Tree, kích thước con trỏ khóa ngoại và tốc độ phép nối (JOIN).
2. **Khóa Nghiệp vụ Tự nhiên (Natural Business Keys):** Sử dụng trường `record_id VARCHAR(150)` (nguồn gốc từ trường `id` trong JSON, ví dụ `bridge_45228`, `guardrail_521041`) kết hợp ràng buộc `UNIQUE`. Khóa này đảm bảo tính tương thích với hệ thống gốc Vidagis và cho phép liên kết phi phụ thuộc với tài liệu và báo cáo.
3. **Khóa Ràng buộc Chống Trùng lặp (Idempotent Compound Keys):** Cặp `(dataset_key, record_key)` tại tầng Raw Ingestion ngăn chặn việc nạp trùng dữ liệu khi chạy lại batch import.

---

## 2. Ma trận Toàn vẹn Tham chiếu (Referential Integrity Matrix)

| Bảng nguồn (Nhiều) | Cột Khóa ngoại (FK) | Bảng đích (Một) | Cột Khóa chính (PK) | Hành vi Xóa (ON DELETE) | Mục đích nghiệp vụ |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `import_file` | `job_id` | `import_job` | `id` | `CASCADE` | Xóa nhật ký phiên nạp thì xóa toàn bộ tệp liên quan |
| `import_error` | `job_id` | `import_job` | `id` | `CASCADE` | Xóa nhật ký phiên nạp thì xóa danh sách lỗi |
| `raw_dataset_record` | `dataset_key` | `dataset_registry` | `dataset_key` | `RESTRICT` | Bảo vệ danh mục dataset đang có dữ liệu thô |
| `raw_dataset_record` | `import_job_id` | `import_job` | `id` | `SET NULL` | Giữ lại dữ liệu thô ngay cả khi dọn dẹp log phiên nạp |
| `asset_record` | `raw_record_id` | `raw_dataset_record` | `id` | `SET NULL` | Truy vết ngược về payload JSON gốc của tài sản |
| `asset_geometry` | `asset_id` | `asset_record` | `id` | `CASCADE` | Xóa tài sản thì tự động xóa hình học không gian tương ứng |
| `document_folder` | `parent_folder_id` | `document_folder` | `id` | `SET NULL` | Duy trì cây thư mục tài liệu tự tham chiếu (Self-join) |
| `document_metadata` | `folder_id` | `document_folder` | `id` | `SET NULL` | Xóa thư mục không làm mất file đính kèm |
| `document_metadata` | `asset_record_id` | `asset_record` | `record_id` | `RESTRICT` | Liên kết hồ sơ hoàn công / bản vẽ với công trình tài sản |
| `app_user` | `role_id` | `app_role` | `id` | `RESTRICT` | Không thể xóa vai trò khi còn tài khoản đang sử dụng |
| `app_permission` | `role_id` | `app_role` | `id` | `CASCADE` | Xóa vai trò thì xóa các phân quyền tương ứng |
| `audit_log` | `user_id` | `app_user` | `id` | `SET NULL` | Giữ nhật ký kiểm toán ngay cả khi tài khoản bị xóa |

---

## 3. Danh mục Toàn bộ Chỉ mục (Index Catalog)

### 3.1 Chỉ mục B-Tree Thông thường và Khóa Ngoại

| Tên Chỉ mục | Bảng áp dụng | Cột đánh chỉ mục | Loại chỉ mục | Mục đích tối ưu hóa |
| :--- | :--- | :--- | :--- | :--- |
| `pk_asset_record` | `asset_record` | `id` | B-Tree (PK) | Tra cứu nhanh theo ID nội bộ |
| `uq_asset_record_rid` | `asset_record` | `record_id` | B-Tree (UNIQUE) | Ràng buộc duy nhất, tra cứu theo mã nguồn |
| `idx_asset_record_raw` | `asset_record` | `raw_record_id` | B-Tree | Tối ưu hóa phép JOIN ngược về tầng Raw |
| `idx_asset_record_parent` | `asset_record` | `parent_id` | B-Tree Partial | Tối ưu truy vấn cây phân cấp đệ quy (Recursive CTE) |
| `idx_asset_record_type` | `asset_record` | `asset_type, is_deleted` | B-Tree Composite | Lọc danh sách theo loại công trình trên giao diện |
| `idx_asset_record_dataset` | `asset_record` | `dataset_code, is_deleted` | B-Tree Composite | Lọc nhanh theo dataset nguồn (VD: `tbl_bridge`) |
| `idx_asset_record_state` | `asset_record` | `state` | B-Tree Partial | Lọc tài sản chờ duyệt (`state = 'Pending'`) |
| `idx_ref_catalog_code` | `reference_catalog` | `catalog_code, sort_order` | B-Tree Composite | Lấy danh mục hiển thị trên combobox / dropdown |
| `idx_doc_meta_asset` | `document_metadata` | `asset_record_id` | B-Tree Partial | Lấy tài liệu đính kèm khi mở trang chi tiết tài sản |

---

### 3.2 Chỉ mục B-Tree Đa cột Phức hợp (Composite Indexes)

#### Chỉ mục Phân vị Tuyến và Lý trình:
```sql
CREATE INDEX idx_asset_record_route_km 
ON asset_record(route_code, km_from, km_to) 
WHERE is_deleted = FALSE;
```
- **Nguyên lý:** Tuân thủ quy tắc tiền tố bên trái (Leftmost Prefix Rule).
- **Hỗ trợ tối ưu:**
  - `WHERE route_code = 'QL.1'`
  - `WHERE route_code = 'QL.1' AND km_from >= 100 AND km_to <= 200`
  - `ORDER BY route_code ASC, km_from ASC`

#### Chỉ mục Phân quyền Vùng Dữ liệu (Data Scope Filtering):
```sql
CREATE INDEX idx_asset_record_scope 
ON asset_record(branch_id, province_id) 
WHERE is_deleted = FALSE;
```
- **Hỗ trợ tối ưu:** Khóa chặt dữ liệu theo tài khoản cán bộ trực thuộc Khu QLĐB I (`branch_id = 'kqldb_1'`) hoặc Sở GTVT Lạng Sơn (`province_id = 'Tỉnh Lạng Sơn'`).

---

### 3.3 Chỉ mục Điều kiện Một phần (Partial Indexes)

Tất cả các chỉ mục phục vụ truy vấn người dùng trên bảng `asset_record` đều được gắn thêm điều kiện lọc `WHERE is_deleted = FALSE`:
- **Lợi ích:**
  1. **Tiết kiệm dung lượng bộ nhớ RAM đệm (Buffer Pool):** Loại bỏ hoàn toàn các bản ghi đã xóa mềm khỏi cây chỉ mục, giảm kích thước index từ 15% đến 30%.
  2. **Tăng tốc độ ghi (Insert/Update):** Khi thực hiện các thao tác cập nhật dữ liệu lịch sử hoặc xóa mềm, cơ sở dữ liệu không phải tái cân bằng toàn bộ nhánh cây index nghiệp vụ.

---

### 3.4 Chỉ mục Không gian Địa lý PostGIS (Spatial GiST Indexes)

Bắt buộc sử dụng chỉ mục GiST (Generalized Search Tree) trên bảng `asset_geometry` để phục vụ WebGIS:

```sql
-- 1. Chỉ mục không gian tổng quát cho toàn bộ đối tượng (Point, Line, Polygon):
CREATE INDEX idx_asset_geom_gist 
ON asset_geometry USING GIST (geom);

-- 2. Chỉ mục không gian riêng biệt cho điểm (Point) để tối ưu gom cụm (Clustering):
CREATE INDEX idx_asset_geom_point_gist 
ON asset_geometry USING GIST (point_geom) 
WHERE point_geom IS NOT NULL;

-- 3. Chỉ mục không gian riêng biệt cho tuyến (LineString) để tối ưu cắt tuyến:
CREATE INDEX idx_asset_geom_line_gist 
ON asset_geometry USING GIST (line_geom) 
WHERE line_geom IS NOT NULL;
```

#### Đánh giá Hiệu năng GiST Index:
- Sử dụng thuật toán phân cấp hộp bao R-Tree.
- Truy vấn không gian theo khung nhìn viewport:
  `ag.geom && ST_MakeEnvelope(minX, minY, maxX, maxY, 4326)`
- Cho phép quét và trích xuất 1,000 đối tượng trên bản đồ từ tập 833,000 đối tượng trong thời gian **$< 15\text{ms}$**, không xảy ra quét toàn bảng (Seq Scan).

---

### 3.5 Chỉ mục Đảo GIN (Generalized Inverted Index)

#### Chỉ mục GIN JSONB Path Ops cho Thuộc tính Động (`attributes`):
```sql
CREATE INDEX idx_asset_record_attrs_gin 
ON asset_record USING GIN (attributes jsonb_path_ops);
```
- **Sử dụng `jsonb_path_ops` thay vì `jsonb_ops` mặc định:**
  - Kích thước chỉ mục nhỏ hơn **~60%** so với `jsonb_ops`.
  - Tốc độ kiểm tra toán tử chứa `@>` (`attributes @> '{"type_guardrail": "Hộ lan tôn sóng"}'`) nhanh hơn đáng kể.

#### Chỉ mục Trigram GIN phục vụ Tìm kiếm Mờ Tiếng Việt (Fuzzy Full-text Search):
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_asset_record_name_trgm 
ON asset_record USING GIN (name gin_trgm_ops);
```
- **Hỗ trợ tối ưu:**
  - Cho phép người dùng gõ từ khóa không đầy đủ hoặc không dấu: `WHERE name ILIKE '%cầu dần xây%'` hoặc `WHERE name % 'Dần Xây'`.
  - Tốc độ phản hồi đạt mức thời gian thực (Real-time Autocomplete / Search-as-you-type) dưới **$30\text{ms}$**.

---

## 4. Cấu hình Tham số Vận hành Chỉ mục (Tuning & Maintenance)

### 4.1 Cấu hình HOT (Heap-Only Tuples) Update
Bảng `asset_record` thường xuyên có các thao tác cập nhật trạng thái duyệt (`state`) và số phiên bản (`version`). Để tránh việc cập nhật một cột làm phải ghi lại toàn bộ các chỉ mục (Index Bloat), thiết lập hệ số điền đầy:
```sql
ALTER TABLE asset_record SET (fillfactor = 90);
```
*Tác dụng:* Để dành 10% dung lượng trống trong mỗi khối dữ liệu (Data Page) 8KB, cho phép PostgreSQL thực hiện cập nhật ngay tại khối hiện tại (HOT Update) mà không cần chạm vào các chỉ mục B-Tree và GIN.

### 4.2 Thống kê Bộ lập Kế hoạch (Query Planner Statistics Target)
Đối với các cột có độ lệch phân bổ dữ liệu lớn (như `route_code` với các quốc lộ trọng điểm như QL.1, QL.2 chiếm nhiều bản ghi), nâng cao độ phân giải thống kê:
```sql
ALTER TABLE asset_record ALTER COLUMN route_code SET STATISTICS 500;
ALTER TABLE asset_record ALTER COLUMN branch_id SET STATISTICS 500;
ALTER TABLE asset_record ALTER COLUMN dataset_code SET STATISTICS 500;

ANALYZE asset_record;
ANALYZE asset_geometry;
```
