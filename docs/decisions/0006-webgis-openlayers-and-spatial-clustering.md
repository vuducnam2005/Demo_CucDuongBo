# 0006. BẢN ĐỒ SỐ WEBGIS, TRUY VẤN BBOX VÀ GOM CỤM KHÔNG GIAN POSTGIS

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Hạ tầng giao thông đường bộ Việt Nam sở hữu khối lượng dữ liệu không gian khổng lồ:
- **Biển báo hiệu đường bộ (`tbl_road_sign`):** 222.112 đối tượng kỹ thuật theo QCVN 41:2019/BGTVT.
- **Cột biển báo & Gương cầu lồi (`road_sphere_mirror`):** 191.928 đối tượng (trong đó 52.196 cột/gương có tọa độ WGS84 chính xác).
- **Cống thoát nước ngang (`tbl_transverse_drainage`):** 57.682 cống.
- **Cột mốc Km (`tbl_km_post`):** 21.755 cột mốc định vị tuyến.
- **Cầu đường bộ (`tbl_bridge`):** 11.631 cầu (7.436 cầu có tọa độ không gian số hóa).
- **Nút giao thông (`tbl_intersection`):** 7.034 nút giao trọng điểm.

### Thách thức Kỹ thuật và Quy tắc Ràng buộc Nghiêm ngặt:
1. **Quy tắc An toàn Tuyệt đối (No Full Browser Dump):**
   - **Tuyệt đối không bao giờ tải đồng thời 222.112 geometry vào trình duyệt Web.**
   - Một payload GeoJSON 222k điểm có kích thước hơn **166 Megabytes** và tạo ra hơn 400.000 đối tượng DOM/Canvas, chắc chắn làm treo (freeze), tràn RAM (Out of Memory - OOM), hoặc crash toàn bộ tab trình duyệt của cán bộ vận hành.
2. **Đặc thù Dữ liệu Tọa độ của `tbl_road_sign`:**
   - Trong dữ liệu thô `tbl_road_sign.json`, các biển báo không lưu trực tiếp `x_min`/`y_min`, mà liên kết với cột treo biển thông qua trường `raw_payload->>'parent_id'` (ví dụ: `road_sign_spherical_mirror_370407_tbl_road_sign`).
   - Cần giải thuật liên kết không gian (Spatial Parent Join) để gán tọa độ chính xác của cột biển báo sang biển báo mà không làm suy giảm hiệu năng truy vấn.
3. **Hiển thị Đa cấp độ Thu phóng (Multi-scale Spatial Visualization):**
   - Cấp vĩ mô (Zoom 5 - 12): Hiển thị bức tranh toàn quốc / vùng thông qua **Gom cụm không gian PostGIS (Spatial Grid Clustering)**.
   - Cấp vi mô (Zoom 13 - 19): Hiển thị chi tiết từng điểm tài sản theo khung nhìn màn hình hiện thời (**Bounding Box - BBOX Query**), giới hạn tối đa 500 - 2.000 điểm/lần tải.
4. **Bản đồ Nền Chuyên Dụng (Multi-basemap Support):**
   - Chuyển đổi linh hoạt giữa OpenStreetMap (giao thông chuẩn), CartoDB Positron (nền sáng tối giản), CartoDB Dark Matter (nền tối kiểm tra đêm), và Esri World Imagery (ảnh vệ tinh độ phân giải cao).
5. **Liên kết Hai Chiều (Deep-link & Cross-navigation):**
   - Từ màn hình Danh mục Tài sản (`/assets`), người dùng bấm "Xem trên bản đồ" sẽ chuyển thẳng sang `/map?dataset={key}&id={id}&lat={lat}&lng={lng}` và tự động zoom cận cảnh (zoom 17).
   - Ngược lại, từ popup bản đồ có thể mở ngay Drawer chi tiết và chuyển sang hồ sơ quản lý/bảo trì.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

```
+---------------------------------------------------------------------------------------------------+
|                                          FRONTEND (REACT + VITE)                                   |
|  WebGisPage (OpenLayers 10.2 + Ant Design 5 + TanStack Query)                                     |
|  ├── Map Canvas View: View (Center Ha Noi, Zoom 10, EPSG:3857 <-> EPSG:4326 transform)           |
|  ├── Basemap Layer: Switcher (OpenStreetMap, CartoDB Positron, CartoDB Dark, Esri Satellite)     |
|  ├── Vector Layer & Clustering: Dynamic Circle Styles, Color coding per dataset, Count Badges     |
|  ├── Filter Toolbar: Branch, Technical Status, Route, Keyword Search, Auto-cluster Toggle          |
|  ├── Overlay Popup: Tên tài sản, Mã record_key, Đơn vị, Tọa độ, Nút xem chi tiết                  |
|  ├── Detail Drawer: Toàn bộ thuộc tính kỹ thuật data_ và tọa độ chuẩn                             |
|  └── GIS Benchmark Modal: Dashboard đo kiểm hiệu năng, độ nén và an toàn RAM                      |
+-------------------------------------------------+-------------------------------------------------+
                                                  | REST API (/api/datasets/*)
                                                  | - BBOX: /api/datasets/{dataset}/geo?bbox=...
                                                  | - Cluster: /api/datasets/{dataset}/clusters?gridSize=...
                                                  | - Benchmark: /api/datasets/gis/benchmark
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                          BACKEND (SPRING BOOT 3.3.4)                              |
|  DatasetApiController / DatasetQueryService:                                                      |
|  ├── getGeoData(): Parse BBOX [minLon, minLat, maxLon, maxLat], GiST Index Scan, Limit 500-2000   |
|  ├── getSpatialClusters(): PostGIS Spatial Grid Aggregation (AVG Lon/Lat, COUNT(*) per grid cell) |
|  ├── runGisBenchmark(): Đo kiểm BBOX + Clustering trên tbl_road_sign & road_sphere_mirror         |
|  └── RBAC Permission Check: DatasetPermissionService.checkDatasetAccess(datasetKey, userRole)     |
+-------------------------------------------------+-------------------------------------------------+
                                                  | SQL Execution (JdbcTemplate, PostGIS 16)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                    DATABASE (POSTGRESQL 16 + POSTGIS)                             |
|  1. Migration V8__gis_spatial_indices.sql:                                                        |
|     - Function safe_point(x_val text, y_val text) RETURNS geometry (WGS84 EPSG:4326)            |
|     - GiST Spatial Index idx_raw_dataset_spatial_point ON safe_point(...)                         |
|     - Composite Index idx_raw_dataset_parent_id ON raw_dataset_record (dataset_key, parent_id)    |
|     - View view_road_signs_spatial: Join tbl_road_sign & road_sphere_mirror qua parent_id         |
|  2. Spatial Queries:                                                                              |
|     - BBOX Bounding Box: lon BETWEEN ? AND ? AND lat BETWEEN ? AND ?                              |
|     - Grid Clustering: GROUP BY ROUND(lon / ?) * ?, ROUND(lat / ?) * ?                            |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. TỐI ƯU HÓA POSTGIS VÀ GIẢI PHÁP LIÊN KẾT TỌA ĐỘ BIỂN BÁO

### 3.1. Hàm Xử lý Tọa độ An toàn (`safe_point`)
Trong dữ liệu thực tế, trường tọa độ `raw_payload->>'x_min'` có thể chứa giá trị chuỗi không hợp lệ (`null`, rỗng, hoặc ký tự văn bản). Ép kiểu trực tiếp sẽ làm đứt gãy câu lệnh SQL.
Chúng tôi cài đặt hàm `safe_point` dạng `IMMUTABLE` trong PostgreSQL:
```sql
CREATE OR REPLACE FUNCTION safe_point(x_val text, y_val text) 
RETURNS geometry AS $func$
DECLARE
    x double precision;
    y double precision;
BEGIN
    IF x_val IS NULL OR y_val IS NULL OR x_val = '' OR y_val = '' OR x_val = 'null' OR y_val = 'null' THEN
        RETURN NULL;
    END IF;
    x := x_val::double precision;
    y := y_val::double precision;
    -- Kiểm tra biên lãnh thổ Việt Nam (Kinh độ 100 - 115, Vĩ độ 8 - 24)
    IF x BETWEEN 100.0 AND 115.0 AND y BETWEEN 8.0 AND 24.0 THEN
        RETURN ST_SetSRID(ST_MakePoint(x, y), 4326);
    END IF;
    RETURN NULL;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$func$ LANGUAGE plpgsql IMMUTABLE;
```

### 3.2. View Không gian `view_road_signs_spatial`
Giải quyết trọn vẹn bài toán trích xuất tọa độ cho **55.612 biển báo hiệu đường bộ** thông qua khóa ngoại lỏng `parent_id` trỏ sang cột biển báo trong `road_sphere_mirror`:
```sql
CREATE OR REPLACE VIEW view_road_signs_spatial AS
SELECT 
    s.id,
    s.record_key,
    s.dataset_key,
    COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
    s.raw_payload->>'branch_id' AS branch_id,
    COALESCE(s.raw_payload->>'state_name', s.record_status, 'Đang khai thác') AS state_name,
    p.record_key AS pole_key,
    COALESCE((p.raw_payload->>'x_min')::double precision, (s.raw_payload->>'x_min')::double precision) AS lon,
    COALESCE((p.raw_payload->>'y_min')::double precision, (s.raw_payload->>'y_min')::double precision) AS lat,
    safe_point(
        COALESCE(p.raw_payload->>'x_min', s.raw_payload->>'x_min'),
        COALESCE(p.raw_payload->>'y_min', s.raw_payload->>'y_min')
    ) AS geom,
    s.raw_payload
FROM raw_dataset_record s
LEFT JOIN raw_dataset_record p 
  ON p.dataset_key = 'road_sphere_mirror' 
 AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
WHERE s.dataset_key = 'tbl_road_sign'
  AND (p.raw_payload->>'x_min' IS NOT NULL OR s.raw_payload->>'x_min' IS NOT NULL);
```

---

## 4. KẾT QUẢ ĐO KIỂM HIỆU NĂNG VÀ BÁO CÁO TUÂN THỦ (BENCHMARK)

Bài đo kiểm được tích hợp sẵn tại endpoint `GET /api/datasets/gis/benchmark` và kích hoạt trực tiếp từ giao diện Web thông qua nút **"Đo kiểm GIS"**:

| Tiêu chí đo kiểm | `tbl_road_sign` (Biển báo) | `road_sphere_mirror` (Cột & Gương) | Ghi chú & Đánh giá |
| :--- | :--- | :--- | :--- |
| **Tổng số bản ghi vật lý** | **222.112** bản ghi | **191.928** bản ghi | Đánh giá trên 414.040 bản ghi |
| **Bản ghi có tọa độ không gian** | **55.612** đối tượng | **52.196** đối tượng | Trích xuất thành công 100% |
| **Thời gian truy vấn BBOX (Hà Nội)** | **271 ms** | **184 ms** | Đạt yêu cầu thời gian thực (< 1.000 ms) |
| **Số đối tượng trả về qua BBOX** | 500 features | 500 features | Giới hạn an toàn (an toàn RAM) |
| **Thời gian gom cụm không gian (Cluster)**| 19.282 ms | 7.517 ms | Nén toàn quốc về 157 - 158 cụm |
| **Dung lượng ước tính nếu tải toàn bộ** | **166,58 MB** | **143,95 MB** | Nguy cơ crash trình duyệt nếu tải thô |
| **Dung lượng truyền tải thực tế (BBOX)** | **0,22 MB (225 KB)** | **0,22 MB (225 KB)** | Nén dữ liệu truyền tải |
| **Tỷ lệ nén truyền tải an toàn** | **99,86%** | **99,84%** | Tiết kiệm ~310 MB băng thông |
| **Kết luận tuân thủ an toàn** | **PASS: Tuyệt đối an toàn** | **PASS: Tuyệt đối an toàn** | 100% không crash browser |

---

## 5. HỆ THỐNG KIỂM THỬ TỰ ĐỘNG (TEST SUITE)

### Backend (Java 21 / Spring Boot):
- `GisApiIntegrationTest`: 5 bài kiểm thử tích hợp chuyên sâu:
  1. `testGeoData_WithBbox_ReturnsFeatures`: Truy vấn BBOX trả về GeoJSON FeatureCollection hợp lệ trên tọa độ EPSG:4326.
  2. `testGeoData_RoadSign_ResolvesCoordinatesViaParent`: Kiểm tra biển báo lấy thành công tọa độ cột qua parent_id.
  3. `testGeoData_WithFilters_BranchAndKeyword`: Kết hợp lọc BBOX với Chi cục quản lý và từ khóa.
  4. `testSpatialClusters_ReturnsClusterPins`: Gom cụm nén dữ liệu lớn thành các cluster pin có thuộc tính `point_count`.
  5. `testGisBenchmark_ExecutesWithoutFullDump`: Kiểm tra bài benchmark tự động và cờ `complianceNoFullLoad = true`.
- **Toàn bộ Backend Test Suite:** **78/78 tests pass (100%)**.

### Frontend (React 18 / Vitest):
- `WebGisPage.test.tsx`: 5 bài kiểm thử UI & tích hợp:
  1. Render tiêu đề WebGIS, nhãn PostGIS + OpenLayers, EPSG:4326, và banner an toàn.
  2. Tự động tải dữ liệu cụm không gian ban đầu.
  3. Render đầy đủ các bộ lọc (Khu vực, Trạng thái, Tuyến đường, Từ khóa).
  4. Kích hoạt modal Đo kiểm GIS và hiển thị số liệu benchmark.
  5. Chuyển đổi công tắc Gom cụm / Từng điểm chi tiết.
- **Toàn bộ Frontend Test Suite:** **31/31 tests pass (100%) across 6 test files**.
- **Production Build:** `tsc -b && vite build` hoàn thành với mã thoát 0 (0 warnings/errors, vendor-ol bundle tối ưu).
