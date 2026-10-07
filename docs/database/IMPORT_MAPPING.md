# QUY TẮC ÁNH XẠ DỮ LIỆU TỪ NGUỒN VÀO SCHEMA (IMPORT MAPPING)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Quy trình Chuyển nạp Toàn trình (End-to-End Pipeline)

Tiến trình nạp dữ liệu được thực thi theo mô hình hai bước rõ rệt (Two-Stage Pipeline):
1. **Giai đoạn 1: Nạp thô (Raw Ingestion):** Đọc các tệp JSON từ ổ đĩa `C:\Data\kcht_json_2026-10-05`, tính toán hash SHA-256, nạp nguyên vẹn từng bản ghi vào `raw_dataset_record` và ghi nhận phiên nạp vào `import_job`, `import_file`.
2. **Giai đoạn 2: Trích xuất & Chuẩn hóa (Curated Transformation):** Thực thi tiến trình Spring Batch hoặc Stored Procedure trong PostgreSQL để phân tích payload `data_`, bóc tách tọa độ không gian PostGIS, làm sạch dữ liệu văn bản và nạp vào các bảng `asset_record`, `asset_geometry`, `reference_catalog`, `document_metadata`.

---

## 2. Ánh xạ Tầng Raw Ingestion

| Cột đích trong `raw_dataset_record` | Nguồn dữ liệu từ JSON / Tệp | Quy tắc biến đổi & Ghi chú |
| :--- | :--- | :--- |
| `dataset_code` | Tên tệp bỏ đuôi `.json` (VD: `duonggom`, `tbl_bridge`) | Lấy từ `manifest.json -> files[i].path` |
| `record_key` | Trường `id` hoặc `gid` của bản ghi JSON | Ưu tiên lấy `item["id"]`, nếu rỗng lấy `item["gid"]` |
| `source_file` | Đường dẫn tệp tương đối (VD: `assets/duonggom.json`) | Đường dẫn từ root dữ liệu |
| `payload_hash` | SHA-256 của chuỗi JSON nguyên bản | Tính toán bằng thuật toán SHA-256 chuẩn |
| `payload_json` | Toàn bộ object JSON của bản ghi | Ép kiểu `JSONB` trong PostgreSQL |
| `import_job_id` | Khóa ngoại tới `import_job.id` | Tạo mới mỗi khi bắt đầu một phiên import |
| `parse_status` | Mặc định `'PENDING'` | Chuyển sang `'PROCESSED'` sau khi sang Curated |

---

## 3. Ánh xạ Tài sản Hạ tầng (`asset_record`)

Mỗi bản ghi trong 57 tệp `assets/*.json` được ánh xạ thành 1 dòng trong `asset_record`.

### 3.1 Các trường định danh và phong bì chung (Envelope Fields)

| Cột trong `asset_record` | Trường nguồn JSON | Quy tắc chuyển đổi |
| :--- | :--- | :--- |
| `record_id` | `item["id"]` | `VARCHAR(150)` - Khóa định danh nghiệp vụ bắt buộc |
| `gid` | `item["gid"]` | Ép kiểu sang `BIGINT` |
| `dataset_code` | Tên dataset (VD: `tbl_bridge`) | Chuỗi chuẩn hóa tên bảng nguồn |
| `asset_type` | Suy luận từ `dataset_code` | Ánh xạ mã phân loại (Xem bảng danh mục loại tài sản) |
| `name` | `item["fielddisplay"]` | Tên tiếng Việt hiển thị của công trình |
| `organization_id` | `item["organization_id"]` | Mặc định `'moc_dbvn'` nếu null |
| `branch_id` | `item["branch_id"]` | Mã chi nhánh quản lý (VD: `'kqldb_1'`, `'sxd_tq'`) |
| `branch_name` | `item["branch_name"]` | Tên hiển thị của chi nhánh (nếu có) |
| `state` | `item["state"]` | Trạng thái duyệt (VD: `'Approved'`, `'Pending'`) |
| `state_name` | `item["state_name"]` | Tên hiển thị trạng thái tiếng Việt (VD: `'Đã duyệt'`) |
| `level_state` | `item["level_state"]` | Cấp phê duyệt (VD: `'cdb_vn'`) |
| `parent_id` | `item["parent_id"]` | Làm sạch hậu tố (Xem mục 5.3) |

### 3.2 Bóc tách mảng thuộc tính động `data_`
Thuộc tính `data_` trong nguồn là một chuỗi JSON serialize dạng mảng:
`[{"column_code": "...", "column_name": "...", "column_value": "...", "column_identify": "..."}]`

Quy tắc unpack: Duyệt từng phần tử trong mảng, căn cứ vào `column_identify` để trích xuất ra các cột Typed của `asset_record`:

| Cột trong `asset_record` | `column_identify` trong `data_` | Quy tắc làm sạch & Chuyển đổi |
| :--- | :--- | :--- |
| `route_code` | `route_code`, `route_name`, `tuyen` | Trích xuất mã tuyến (VD: `'QL.1'`, `'QL.2'`) |
| `km_from` | `km_from`, `lytrinh` | Xóa thẻ HTML, bóc tách giá trị số km dạng `NUMERIC(10,3)` |
| `km_to` | `km_to` | Xóa thẻ HTML, bóc tách giá trị số km dạng `NUMERIC(10,3)` |
| `lytrinh` | `lytrinh` hoặc `value_display` | Chuỗi hiển thị sạch (VD: `'Km 128 + 000'`) |
| `province_id` / `name` | `province_from_id`, `tinhthanhpho` | Tên tỉnh/thành phố (VD: `'Tỉnh Lạng Sơn'`) |
| `district_name` | `district_from_id`, `quanhuyen` | Tên quận/huyện |
| `town_name` | `town_from_id`, `xaphuong` | Tên xã/phường (VD: `'Phường An Tường'`) |
| `road_class` | `capduong`, `road_class_id` | Cấp kỹ thuật đường (VD: `'Cấp III'`) |
| `road_type` | `roadtype` | Loại đường (VD: `'Quốc lộ'`, `'Đường cao tốc'`) |
| `construction_year` | `construction_year`, `namxaydung` | Ép kiểu `INTEGER` |
| `maintain_value` | `maintain_value`, `giatritaisan` | Ép kiểu `NUMERIC(18,2)` sau khi loại bỏ dấu phân cách |
| `active_status` | `active_status` | Tình trạng khai thác |
| `attributes` | Toàn bộ các thuộc tính còn lại | Chuyển đổi thành cặp key-value sạch `{ "<column_identify>": "<column_value>" }` dạng `JSONB` |

---

## 4. Ánh xạ Tọa độ và Không gian Địa lý (`asset_geometry`)

Hệ tọa độ quy định thống nhất: **WGS 84 (EPSG:4326)**.

### 4.1 Quy tắc Nhận diện và Trích xuất Tọa độ

Dữ liệu nguồn lưu tọa độ ở hai nơi:
1. **Tại phong bì ngoài (Bounding Box):** `x_min`, `y_min`, `x_max`, `y_max`
2. **Bên trong mảng `data_`:**
   - Điểm đầu: `from_coordinatex` (Kinh độ X), `from_coordinatey` (Vĩ độ Y)
   - Điểm cuối: `to_coordinatex` (Kinh độ X), `to_coordinatey` (Vĩ độ Y)

### 4.2 Thuật toán Tạo Hình học PostGIS

```
if (x_min is not null and y_min is not null):
    if (x_min == x_max and y_min == y_max):
        geom_type = 'POINT'
        geom = ST_SetSRID(ST_MakePoint(x_min, y_min), 4326)
    else:
        geom_type = 'LINESTRING' (hoặc BBOX Polygon)
        geom = ST_SetSRID(ST_MakeLine(ST_MakePoint(x_min, y_min), ST_MakePoint(x_max, y_max)), 4326)

else if (from_coordinatex is not null and from_coordinatey is not null):
    lon1 = ParseFloat(from_coordinatex)
    lat1 = ParseFloat(from_coordinatey)
    
    if (ValidateCoordinatesInVietnam(lon1, lat1)):
        if (to_coordinatex is not null and to_coordinatey is not null):
            lon2 = ParseFloat(to_coordinatex)
            lat2 = ParseFloat(to_coordinatey)
            if (ValidateCoordinatesInVietnam(lon2, lat2)):
                geom_type = 'LINESTRING'
                geom = ST_SetSRID(ST_MakeLine(ST_MakePoint(lon1, lat1), ST_MakePoint(lon2, lat2)), 4326)
            else:
                geom_type = 'POINT'
                geom = ST_SetSRID(ST_MakePoint(lon1, lat1), 4326)
        else:
            geom_type = 'POINT'
            geom = ST_SetSRID(ST_MakePoint(lon1, lat1), 4326)
```

### 4.3 Giới hạn Kiểm tra Tính Hợp lệ Tọa độ tại Việt Nam
Tọa độ được coi là hợp lệ nếu nằm trong phạm vi lãnh thổ và vùng biển Việt Nam:
- **Kinh độ (Longitude - X):** $102.0^\circ \le X \le 112.0^\circ$
- **Vĩ độ (Latitude - Y):** $8.0^\circ \le Y \le 24.0^\circ$
- Các bản ghi có tọa độ $= 0$ hoặc nằm ngoài dải này được đánh dấu `is_valid = FALSE` và lưu tọa độ thô vào `attributes` để xem xét xử lý sau, tránh làm sai lệch hiển thị bản đồ.

---

## 5. Xử lý Dữ liệu Biên (Edge Cases & Data Sanitization)

### 5.1 Làm sạch Thẻ HTML trong Giá trị Lý trình
Trong nguồn, các trường lý trình thường bị bọc trong thẻ HTML:
- *Giá trị gốc:* `<span>Lý trình: Km 128 + 000</span>` hoặc `<span>Km lý trình: Km 0 + 000</span>`
- *Quy tắc xử lý:* 
  1. Loại bỏ các thẻ HTML bằng Regular Expression: `s/<[^>]*>//g`.
  2. Loại bỏ các chuỗi thừa: `"Lý trình:"`, `"Km lý trình:"`.
  3. Giá trị chuỗi hiển thị sạch: `'Km 128 + 000'`.
  4. Trích xuất giá trị số: `128 + 0/1000 = 128.000` km (ghi vào cột `km_from` dạng `NUMERIC(10,3)`).

### 5.2 Xử lý Định dạng Số và Dấu Phẩy/Dấu Chấm Thập phân
Nguồn dữ liệu có sự không nhất quán giữa định dạng kiểu Việt Nam và quốc tế:
- Trường `chieudaithucte`: `'6.701'` (km) trong khi `value_display` là `'6,701'`.
- Trường `maintain_value`: Có thể chứa dấu phẩy phân cách hàng nghìn.
- *Quy tắc xử lý:* Loại bỏ dấu cách, thay thế dấu phẩy bằng dấu chấm trước khi parse sang kiểu số thực / numeric trong Java/SQL.

### 5.3 Chuẩn hóa Khóa Phân cấp Cha - Con (`parent_id`)
Trong hệ thống Vidagis nguồn:
- Tài sản `duonggom`: `parent_id = "17791990530353074_duonggom"`. Trong đó `17791990530353074` là `id` của đoạn tuyến cha trong `tbl_segment`.
- Kết cấu nhịp `tbl_pierstr`: `parent_id = "bridge_22115_tbl_pierstr"`. Trong đó `bridge_22115` là `id` của cầu cha trong `tbl_bridge`.
- *Quy tắc chuẩn hóa:* Tách chuỗi theo dấu gạch dưới cuối cùng để tìm ra `parent_record_id` thực sự:
  `parent_id = REGEXP_REPLACE(raw_parent_id, '_[a-zA-Z0-9]+$', '')`.

---

## 6. Ánh xạ Danh mục Tham chiếu (`reference_catalog`)

Nạp từ 152 tệp `reference_moc_*.json` và tệp `road_sign_catalog.json`:

| Cột `reference_catalog` | Nguồn từ JSON danh mục | Quy tắc chuyển đổi |
| :--- | :--- | :--- |
| `catalog_code` | Tên tệp bỏ tiền tố `reference_moc_dbvn_c_c_` và đuôi `.json` | Ví dụ: `capduong`, `loaithanhphan` |
| `item_code` | `item["vidagis_fieldvalue"]` hoặc `item["vidagis_id"]` | Mã phần tử danh mục (VD: `'03'`, `'W.247'`) |
| `item_name` | `item["vidagis_fielddisplay"]` hoặc `item["vidagis_name_vn"]` | Tên tiếng Việt hiển thị |
| `item_name_en` | `item["vidagis_fielddisplay_en"]` hoặc `item["vidagis_name_en"]` | Tên tiếng Anh |
| `parent_code` | `item["vidagis_parent_value"]` | Mã cấp cha trong danh mục |
| `sort_order` | `item["vidagis_weight"]` | Ép kiểu `INTEGER` |
| `is_active` | `item["vidagis_is_use"]` | Mặc định `TRUE` |
| `extra_attributes` | `item["vidagis_color"]`, `item["vidagis_img"]`, kích thước biển báo | Gói thành `JSONB` |

---

## 7. Ánh xạ Hồ sơ Tài liệu & Chỉ mục Tệp Nhị phân

Kết hợp dữ liệu từ `modules/documents.json`, `document_folders.json` và `document_files_index.json`:

| Cột `document_metadata` | Nguồn dữ liệu | Quy tắc xử lý |
| :--- | :--- | :--- |
| `file_entry_id` | `item["file_id"]` hoặc `item["vidagis_fileentryid"]` | Khóa định danh file duy nhất |
| `original_name` | `item["original_name"]` hoặc `item["vidagis_name"]` | Tên tệp đính kèm |
| `file_extension` | `item["vidagis_extension"]` | Đuôi tệp (pdf, xlsx, docx...) |
| `mime_type` | `item["vidagis_mimetype"]` | MIME type chuẩn |
| `file_size` | `item["bytes"]` hoặc `item["vidagis_size"]` | Dung lượng byte nguyên bản |
| `local_path` | `item["local_file"]` | Đường dẫn tệp lưu trữ trên đĩa |
| `sha256` | `item["sha256"]` | Băm kiểm tra tính toàn vẹn |
| `folder_id` | `item["vidagis_group_id"]` | Liên kết sang `document_folder` |
| `asset_record_id` | `item["vidagis_classpk"]` hoặc `item["vidagis_asset_name"]` | Liên kết sang `asset_record.record_id` |
| `uploader_username` | `item["vidagis_username"]` | Tên cán bộ tải lên hồ sơ |
| `uploaded_at` | `item["vidagis_createdate"]` | Thời điểm tải lên |
