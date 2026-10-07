# ĐẶC TẢ HỆ THỐNG REST API KHAI THÁC DỮ LIỆU KCHT ĐƯỜNG BỘ

**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**  
**Phiên bản API:** `1.1.0` (Hoàn thiện Giai đoạn 4)  
**Công nghệ:** Spring Boot 3.3.4 (Java 21) + PostgreSQL 16 + PostGIS 3.4 + OpenAPI 3.0 (Swagger UI)  
**Cổng phục vụ mặc định:** `http://localhost:8089`  
**Swagger UI:** `http://localhost:8089/swagger-ui/index.html`  
**OpenAPI Spec:** `http://localhost:8089/v3/api-docs`  

---

## 1. TỔNG QUAN KIẾN TRÚC VÀ NGUYÊN TẮC THIẾT KẾ

Hệ thống cung cấp giao diện lập trình ứng dụng RESTful cho phép Frontend (WebGIS, Dashboard điều hành, Hệ thống tra cứu, Module tài liệu hồ sơ) khai thác an toàn và hiệu năng cao trên khối dữ liệu hơn **1,1 triệu bản ghi** đã được nạp và chuẩn hóa.

### Các nguyên tắc cốt lõi:
1. **Phân trang bắt buộc phía máy chủ và Tính ổn định tuyệt đối (Deterministic Pagination):**
   - Mặc định `page = 0, size = 20`. Kích thước mỗi trang bị khống chế tối đa `size <= 100` để ngăn ngừa việc tải toàn bộ dataset lớn làm nghẽn RAM và mạng.
   - **Thứ tự phân trang xác định (Deterministic Tie-Breaker):** Mọi câu truy vấn phân trang khi sắp xếp theo bất kỳ cột nào đều tự động được gắn kèm khóa phụ `, id ASC`. Điều này đảm bảo triệt tiêu hiện tượng bản ghi bị nhảy cóc hoặc trùng lặp giữa các trang (page boundary shift) khi hai bản ghi có cùng giá trị sort.
2. **Kiến trúc cầu nối hai tầng (Curated Primary / Raw Fallback):**
   - `AssetQueryAdapter` tự động kiểm tra tầng Curated (`asset_record`, `asset_geometry`). Nếu dataset đã được chuẩn hóa và chuyển nạp, truy vấn sẽ đi qua JPA Repository tối ưu.
   - Nếu dataset chưa chuyển nạp, hệ thống kích hoạt cơ chế chuyển tiếp trong suốt (transparent fallback) đọc trực tiếp từ `raw_dataset_record`, đảm bảo tính liên tục của dữ liệu.
3. **Chống tấn công SQL Injection và bảo vệ Schema:** Tuyệt đối không cho phép client truyền tên bảng SQL tùy ý. Mọi yêu cầu đều phải đi qua `dataset_registry` để kiểm tra sự tồn tại. Tên trường lọc và sắp xếp được kiểm tra nghiêm ngặt qua **Allowlist**.
4. **Chuẩn dữ liệu không gian GeoJSON RFC 7946:** Endpoint `/api/datasets/{dataset}/geo` trả về chuẩn `FeatureCollection` tương thích trực tiếp với Leaflet, OpenLayers, Mapbox GL và ArcGIS.
5. **Bảo vệ môi trường sản xuất (Production Profile Security):** Không cấp quyền quản trị ngầm cho người dùng ẩn danh khi ứng dụng chạy ở môi trường `prod` / `production`.

---

## 2. ĐẶC TẢ CHI TIẾT CÁC ENDPOINT REST API

### 2.1. Tra cứu danh sách Dataset
- **URL:** `GET /api/datasets`
- **Mô tả:** Lấy danh sách 658 tập dữ liệu KCHT có hỗ trợ tìm kiếm và lọc theo loại.
- **Tham số truy vấn (Query Params):**
  - `kind` (tùy chọn): Lọc theo phân loại dataset (`asset`, `module`).
  - `q` (tùy chọn): Từ khóa tìm kiếm theo mã hoặc tên tiếng Việt.
  - `page` (mặc định `0`): Chỉ số trang (0-indexed).
  - `size` (mặc định `20`, tối đa `100`): Số lượng bản ghi mỗi trang.
- **Phản hồi mẫu (200 OK):**
```json
{
  "content": [
    {
      "datasetKey": "tbl_bridge",
      "datasetName": "Cầu quốc lộ",
      "kind": "asset",
      "endpoint": "/api/v1/bridge",
      "sourceFile": "assets/tbl_bridge.json",
      "totalRecords": 11631,
      "active": true,
      "createdAt": "2026-10-05T23:12:14.088+07:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 658,
  "totalPages": 33,
  "first": true,
  "last": false
}
```

---

### 2.2. Lấy siêu dữ liệu và từ điển trường của Dataset
- **URL:** `GET /api/datasets/{dataset}/metadata`
- **Mô tả:** Lấy cấu trúc thuộc tính, nhãn hiển thị tiếng Việt, kiểu dữ liệu và cờ tìm kiếm/lọc của từng trường.
- **Header:** `X-User-Role` (Tùy chọn, mặc định `ROLE_ADMIN` ở profile dev).
- **Phản hồi mẫu (200 OK):**
```json
{
  "datasetKey": "tbl_bridge",
  "datasetName": "Cầu quốc lộ",
  "kind": "asset",
  "endpoint": "/api/v1/bridge",
  "sourceFile": "assets/tbl_bridge.json",
  "totalRecords": 11631,
  "geometryType": "POINT",
  "fields": [
    {
      "fieldName": "bridge_name",
      "displayName": "Tên cầu",
      "dataType": "varchar",
      "searchable": true,
      "filterable": true,
      "unit": ""
    },
    {
      "fieldName": "route_code",
      "displayName": "Tuyến quốc lộ",
      "dataType": "varchar",
      "searchable": true,
      "filterable": true,
      "unit": ""
    }
  ]
}
```

---

### 2.3. Tra cứu danh sách bản ghi có phân trang và bộ lọc
- **URL:** `GET /api/datasets/{dataset}/records`
- **Mô tả:** Truy vấn bản ghi dữ liệu chi tiết kèm payload JSON nguyên bản.
- **Tham số truy vấn (Query Params):**
  - `q` (tùy chọn): Từ khóa tìm kiếm trong toàn văn bản ghi.
  - `sort` (tùy chọn): Sắp xếp theo allowlist (`id,asc`, `record_key,desc`, `created_at,desc`, `route_code,asc`,...).
  - `page` (mặc định `0`): Chỉ số trang.
  - `size` (mặc định `20`, tối đa `100`): Kích thước trang.
  - Bộ lọc thuộc tính: `filter_<field_name>=value` hoặc `<field_name>=value` (VD: `route_code=QL.1A`).
- **Phản hồi mẫu (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "datasetKey": "tbl_bridge",
      "recordKey": "17786806577720671",
      "payload": {
        "bridge_name": "Cầu Trà Bồng",
        "route_code": "QL.1A",
        "x_min": 108.089699,
        "y_min": 16.222317,
        "length": 185.5
      },
      "recordStatus": "RAW_STORED",
      "createdAt": "2026-10-05T23:13:35.120+07:00",
      "updatedAt": "2026-10-05T23:13:35.120+07:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 11631,
  "totalPages": 582,
  "first": true,
  "last": false
}
```

---

### 2.4. Xem chi tiết một bản ghi theo ID hoặc Record Key
- **URL:** `GET /api/datasets/{dataset}/records/{id}`
- **Mô tả:** Lấy chi tiết một bản ghi đơn lẻ theo ID CSDL hoặc `record_key` nghiệp vụ.
- **Mã lỗi:** Trả về `404 Not Found` nếu bản ghi không tồn tại.

---

### 2.5. Lấy dữ liệu không gian GeoJSON theo Bounding Box
- **URL:** `GET /api/datasets/{dataset}/geo`
- **Mô tả:** Trả về đối tượng `FeatureCollection` GeoJSON tiêu chuẩn (WGS84 EPSG:4326) để hiển thị lên bản đồ WebGIS.
- **Tham số truy vấn (Query Params):**
  - `bbox` (chuỗi dạng `minLon,minLat,maxLon,maxLat`): Bounding box của khung nhìn bản đồ (VD: `bbox=105.0,10.0,108.0,21.0`).
  - Hoặc tham số rời: `minLon`, `minLat`, `maxLon`, `maxLat`.
  - `limit` (mặc định `500`, tối đa `1000`): Giới hạn số lượng feature trả về trong 1 viewport request.
- **Phản hồi mẫu (200 OK):**
```json
{
  "type": "FeatureCollection",
  "totalFeatures": 25,
  "features": [
    {
      "type": "Feature",
      "id": "17786806577720671",
      "geometry": {
        "type": "Point",
        "coordinates": [108.089699, 16.222317]
      },
      "properties": {
        "dataset_key": "tbl_bridge",
        "record_key": "17786806577720671",
        "bridge_name": "Cầu Trà Bồng",
        "route_code": "QL.1A"
      }
    }
  ]
}
```

---

### 2.6. Tra cứu danh mục dùng chung (Reference Catalogs)
- **URL:** `GET /api/reference-catalogs/{catalog}` và `GET /api/catalogs/{catalog}`
- **Mô tả:** Tra cứu các bảng danh mục chuẩn (tỉnh thành, cấp đường, biển báo, loại mặt đường,...). Hỗ trợ cả 2 tiền tố route URL.
- **URL Tra cứu phần tử chi tiết:** `GET /api/reference-catalogs/{catalog}/{itemCode}` hoặc `GET /api/catalogs/{catalog}/{itemCode}`
- **Phản hồi danh sách mẫu (200 OK):**
```json
{
  "content": [
    {
      "catalogCode": "c_tinhthanhpho",
      "itemCode": "01",
      "itemName": "Thành phố Hà Nội",
      "parentCode": "02",
      "sortOrder": 3,
      "active": true,
      "extraAttributes": {
        "vidagis_tableid": "moc_dbvn_c_tinhthanhpho"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 34,
  "totalPages": 2,
  "first": true,
  "last": false
}
```
- **Phản hồi chi tiết phần tử mẫu (200 OK):**
```json
{
  "catalogCode": "c_tinhthanhpho",
  "itemCode": "01",
  "itemName": "Thành phố Hà Nội",
  "parentCode": null,
  "sortOrder": 1,
  "active": true,
  "extraAttributes": {
    "vidagis_fieldvalue": "01",
    "vidagis_fielddisplay": "Thành phố Hà Nội"
  }
}
```

---

### 2.7. Lấy danh sách cây thư mục hồ sơ tài liệu
- **URL:** `GET /api/documents/folders`
- **Mô tả:** Trả về cây thư mục hồ sơ tài liệu kèm số lượng tài liệu trong mỗi thư mục.
- **Phản hồi mẫu (200 OK):**
```json
[
  {
    "id": "root",
    "folderCode": "root",
    "folderName": "Quản lý tài liệu",
    "parentId": "#",
    "count": 0
  },
  {
    "id": "cdb_vn",
    "folderCode": "cdb_vn",
    "folderName": "Cục Đường bộ Việt Nam (14)",
    "parentId": "root",
    "count": 14
  }
]
```

---

### 2.8. Tra cứu và lọc danh sách hồ sơ tài liệu
- **URL:** `GET /api/documents`
- **Tham số truy vấn (Query Params):**
  - `q` (tùy chọn): Tìm kiếm theo tên tài liệu, đối tượng công trình, người tải.
  - `folderId` (tùy chọn): Lọc theo mã đơn vị / nhóm thư mục (VD: `sxd_kh`, `kqldb_1`).
  - `mimeType` (tùy chọn): Lọc theo định dạng MIME (VD: `application/pdf`).
  - `extension` (tùy chọn): Lọc theo đuôi tệp (`pdf`, `docx`, `xlsx`).
  - `page`, `size`: Phân trang server-side.
- **Phản hồi mẫu (200 OK):**
```json
{
  "content": [
    {
      "id": "1785752096858455",
      "fileEntryId": "cf57f1de-39fe-40d7-a4f9-08877d48701b",
      "fileName": "VI TRI CONG TRINH.pdf",
      "fileExtension": "pdf",
      "mimeType": "application/pdf",
      "fileSize": 772921,
      "groupId": "sxd_kh",
      "groupName": "Sở Xây dựng tỉnh Khánh Hòa",
      "objectName": "NGUYỄN THỊ ĐỊNH",
      "tableName": "Đường đô thị",
      "uploader": "Nguyễn Linh Ngọc",
      "createdAt": "2026-08-04T14:46:55"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 250,
  "totalPages": 13,
  "first": true,
  "last": false
}
```

---

### 2.9. Tra cứu chi tiết và tải tệp tài liệu nhị phân
- **Tra cứu chi tiết:** `GET /api/documents/{id}` hoặc `GET /api/documents/{id}/metadata`
  - Hỗ trợ tra cứu theo cả ID hệ thống lẫn mã UUID `file_entry_id`.
  - Trả về `DocumentItemDto` (200 OK) hoặc `404 Not Found`.
- **Tải tệp tin nhị phân:** `GET /api/documents/{id}/file`
  - Trả về dòng byte của tệp tin.
  - Đi kèm tiêu đề HTTP: `Content-Disposition: attachment; filename="..."` và `Content-Type` chuẩn MIME.

---

## 3. CƠ CHẾ BẢO MẬT VÀ PHÒNG VỆ AN TOÀN

1. **Phòng chống SQL Injection:**
   - Client chỉ được truyền mã `dataset` đã đăng ký trong `dataset_registry`. Tên bảng vật lý trong SQL là cố định (`raw_dataset_record`).
   - Mọi ký tự định danh không hợp lệ ngoài biểu thức chính quy `^[a-zA-Z0-9_]{1,50}$` đều bị chặn ngay tại tầng Controller với mã `400 Bad Request`.
2. **Allowlist cho Sort và Filter:**
   - Trường sort bắt buộc phải nằm trong tập: `id`, `record_key`, `created_at`, `imported_at`, `updated_at`, `name`, `code`, `route_code`, `status`, `lytrinh`, `province_id`, `branch_id`.
   - Nếu client truyền cột bất hợp pháp (VD: `sort=malicious_col;--`), hệ thống trả về `400 Bad Request`.
3. **Phân quyền truy cập Dataset (RBAC):**
   - Header `X-User-Role: ROLE_VIEWER`: Chỉ được xem dữ liệu tài sản công khai và danh mục. Bị chặn truy cập (`403 Forbidden`) đối với các bảng quản trị nội bộ hoặc phân hệ bảo trì chi tiết (`maintenance_detail_*`).
   - Header `X-User-Role: ROLE_OPERATOR`: Có quyền xem và quản lý tài sản, cập nhật dữ liệu bảo trì, nhưng bị chặn truy cập các bảng kiểm toán và người dùng nhạy cảm (`audit_log`, `app_user`).
   - Header `X-User-Role: ROLE_ADMIN`: Toàn quyền truy cập.
   - **Chế độ sản xuất (`prod` / `production`):** Khi thiếu header hoặc không xác thực, người dùng ẩn danh tự động bị giới hạn ở `ROLE_VIEWER`, tuyệt đối không cho phép tự phong `ROLE_ADMIN`.

---

## 4. KẾT QUẢ KIỂM THỬ TÍCH HỢP TOÀN DỰ ÁN

Toàn bộ **50 bài kiểm thử** (Integration Tests và Unit Tests) trên PostgreSQL 16 + PostGIS thật đã vượt qua 100%:
- `DatasetApiPostgisIntegrationTest`: 10/10 passed (BBOX GeoJSON, server-side pagination, allowlist filter/sort, RBAC).
- `DatasetApiContractIntegrationTest`: 5/5 passed (Catalog alias & item lookup, document metadata & binary download, deterministic pagination tie-breaker).
- `DatasetPermissionServiceTest`: 5/5 passed (RBAC matrix, dev anonymous admin vs prod anonymous lockdown).
- `DatabaseHandoffIntegrationTest`: 10/10 passed (JPA entities, PostGIS native BBOX queries, soft-delete audit).
- `DatasetImportSmokeIntegrationTest`: 6/6 passed (Curated bridge adapter invariant check).
- `ActuatorHealthIntegrationTest`: 2/2 passed (Liveness and readiness probes).
- Service tests (`BatchRollbackTest`, `ImportServiceDryRunTest`, `PayloadValidatorTest`, `RecordKeyResolverTest`): 12/12 passed.
