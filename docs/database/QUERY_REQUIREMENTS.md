# YÊU CẦU TRUY VẤN VÀ THIẾT KẾ HIỆU NĂNG (QUERY REQUIREMENTS)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Tổng quan Các Mẫu Truy vấn Nghiệp vụ Trọng yếu

Website quản lý hạ tầng giao thông đường bộ phục vụ 6 nhóm ca sử dụng (Use Case) chính với khối lượng dữ liệu hơn 833,000 bản ghi tài sản:
1. **Bảng Điều khiển Tổng hợp (Executive Dashboard):** Thống kê số lượng, chiều dài, giá trị tài sản theo loại, theo cấp đường, theo đơn vị quản lý (Khu QLĐB I - IV, Sở Xây dựng / GTVT các tỉnh).
2. **Bản đồ Số WebGIS (Interactive Map):** Truy vấn không gian theo khung nhìn viewport (Bounding Box), gom cụm (Clustering) và hiển thị lớp chuyên đề.
3. **Tra cứu & Lọc Đa tiêu chí (Multi-faceted Search & Pagination):** Tìm kiếm theo từ khóa tiếng Việt, lọc theo tuyến quốc lộ, khoảng lý trình Km, địa bàn hành chính, trạng thái phê duyệt.
4. **Duyệt Cây Phân cấp Hạ tầng (Hierarchical Traversal):** Tuyến $\rightarrow$ Đoạn tuyến $\rightarrow$ Công trình $\rightarrow$ Bộ phận kết cấu con (Cầu $\rightarrow$ Mố / Trụ / Nhịp).
5. **Tổng hợp Báo cáo Bảo trì Động (Dynamic Maintenance Reporting):** Tự động sinh báo cáo "Chi tiết" và "Tổng hợp" bảo trì mà không cần duy trì 296 bảng tĩnh.
6. **Khai thác Hồ sơ & Bản vẽ Hoàn công (Document Management):** Tải và xem tài liệu kỹ thuật liên kết theo từng công trình.

---

## 2. Đặc tả Chi tiết Các Mẫu Truy vấn Kèm Câu lệnh SQL Chuẩn

### 2.1 Bảng Điều khiển Tổng hợp (Dashboard & KPIs)

#### Mẫu 1: Thống kê số lượng tài sản theo phân loại và chi nhánh quản lý
```sql
SELECT 
    ar.branch_id,
    ar.branch_name,
    ar.asset_type,
    COUNT(ar.id) AS total_assets,
    SUM(COALESCE(ar.maintain_value, 0)) AS total_maintain_value,
    COUNT(CASE WHEN ar.state = 'Approved' THEN 1 END) AS approved_count,
    COUNT(CASE WHEN ar.state = 'Pending' THEN 1 END) AS pending_count
FROM asset_record ar
WHERE (:branchId IS NULL OR ar.branch_id = :branchId)
GROUP BY ar.branch_id, ar.branch_name, ar.asset_type
ORDER BY total_assets DESC;
```
*Index kích hoạt:* `idx_asset_record_branch`, `idx_asset_record_asset_type`.

---

### 2.2 Bản đồ Không gian Địa lý WebGIS (Spatial Viewport & Clustering)

#### Mẫu 2: Lấy danh sách tài sản nằm trong khung nhìn bản đồ (Map Viewport Bounding Box)
Được gọi mỗi khi người dùng kéo (pan) hoặc phóng to/thu nhỏ (zoom) bản đồ:
```sql
SELECT 
    ar.id,
    ar.record_id,
    ar.name,
    ar.asset_type,
    ar.route_code,
    ar.lytrinh,
    ag.geom_type,
    ST_AsGeoJSON(ag.geom) AS geojson
FROM asset_record ar
JOIN asset_geometry ag ON ar.id = ag.asset_id
WHERE ag.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
  AND (:assetType IS NULL OR ar.asset_type = :assetType)
LIMIT 1000;
```
*Index kích hoạt:* `idx_asset_geom_gist` (Sử dụng toán tử bounding-box overlap `&&` tối ưu hóa bởi PostGIS GiST index, thời gian đáp ứng $< 15\text{ms}$).

#### Mẫu 3: Gom cụm điểm bản đồ (Spatial Clustering trên Zoom Level thấp)
Khi ở mức zoom xa (toàn quốc hoặc toàn miền), gom cụm các điểm tài sản dày đặc:
```sql
SELECT 
    cluster_id,
    COUNT(*) AS point_count,
    ST_AsGeoJSON(ST_Centroid(ST_Collect(geom))) AS center_point
FROM (
    SELECT 
        ag.geom,
        ST_ClusterDBSCAN(ag.geom, eps := :epsDegrees, minpoints := 5) OVER () AS cluster_id
    FROM asset_geometry ag
    WHERE ag.geom_type = 'POINT'
      AND ag.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
) sq
WHERE cluster_id IS NOT NULL
GROUP BY cluster_id;
```

---

### 2.3 Tra cứu, Lọc Đa Tiêu chí và Phân trang (Search & Filter)

#### Mẫu 4: Tìm kiếm theo từ khóa kết hợp tuyến, khoảng lý trình và tỉnh thành
```sql
SELECT 
    ar.id,
    ar.record_id,
    ar.name,
    ar.asset_type,
    ar.route_code,
    ar.km_from,
    ar.km_to,
    ar.lytrinh,
    ar.province_name,
    ar.state_name,
    ar.created_at
FROM asset_record ar
WHERE (:keyword IS NULL OR ar.name ILIKE '%' || :keyword || '%' OR ar.record_id ILIKE '%' || :keyword || '%')
  AND (:routeCode IS NULL OR ar.route_code = :routeCode)
  AND (:provinceId IS NULL OR ar.province_id = :provinceId)
  AND (:kmStart IS NULL OR ar.km_to >= :kmStart)
  AND (:kmEnd IS NULL OR ar.km_from <= :kmEnd)
  AND (:assetType IS NULL OR ar.asset_type = :assetType)
ORDER BY ar.route_code ASC, ar.km_from ASC NULLS LAST
OFFSET :offset LIMIT :limit;
```
*Index kích hoạt:* `idx_asset_record_route_km`, `idx_asset_record_province`, `idx_asset_record_name_trgm`.

---

### 2.4 Truy vấn Phân cấp Cha - Con (Hierarchy Tree Traversal)

#### Mẫu 5: Lấy toàn bộ cây tài sản thuộc một tuyến đường hoặc một cầu
Truy vấn đệ quy CTE (Recursive Common Table Expression) từ Cầu xuống các Mố, Trụ và Nhịp cầu:
```sql
WITH RECURSIVE asset_tree AS (
    -- Điểm bắt đầu (Cầu cha hoặc Tuyến cha)
    SELECT 
        ar.id, 
        ar.record_id, 
        ar.name, 
        ar.asset_type, 
        ar.parent_id, 
        1 AS depth,
        ARRAY[ar.record_id] AS path
    FROM asset_record ar
    WHERE ar.record_id = :rootRecordId
    
    UNION ALL
    
    -- Duyệt các thành phần con trực thuộc
    SELECT 
        child.id, 
        child.record_id, 
        child.name, 
        child.asset_type, 
        child.parent_id, 
        parent.depth + 1,
        parent.path || child.record_id
    FROM asset_record child
    JOIN asset_tree parent ON child.parent_id = parent.record_id
)
SELECT * FROM asset_tree ORDER BY path;
```
*Index kích hoạt:* `idx_asset_record_parent`.

---

### 2.5 Báo cáo Bảo trì Động (Dynamic Maintenance Reporting)

Thay vì tạo và bảo trì 296 bảng báo cáo tĩnh, hệ thống sinh trực tiếp báo cáo từ `asset_record`:

#### Mẫu 6: Báo cáo Chi tiết Bảo trì Hộ lan tôn sóng (Tương đương `maintenance_detail_tbl_guardrail_chitiet`)
```sql
SELECT 
    ar.record_id AS vidagis_id,
    ar.lytrinh,
    ar.km_from,
    ar.km_to,
    ar.province_name AS province_from_id,
    ar.attributes->>'type_guardrail' AS type_guardrail,
    ar.maintain_value
FROM asset_record ar
WHERE ar.dataset_code = 'tbl_guardrail'
  AND (:branchId IS NULL OR ar.branch_id = :branchId)
  AND (:routeCode IS NULL OR ar.route_code = :routeCode);
```

#### Mẫu 7: Báo cáo Tổng hợp Bảo trì Cầu đường bộ
```sql
SELECT 
    ar.province_name,
    ar.route_code,
    COUNT(ar.id) AS total_bridges,
    SUM(COALESCE((ar.attributes->>'length')::numeric, 0)) AS total_bridge_length_m,
    SUM(COALESCE(ar.maintain_value, 0)) AS total_maintain_cost
FROM asset_record ar
WHERE ar.dataset_code = 'tbl_bridge'
GROUP BY ar.province_name, ar.route_code
ORDER BY ar.route_code, ar.province_name;
```

---

### 2.6 Khai thác Hồ sơ Tài liệu Gắn liền Tài sản

#### Mẫu 8: Lấy danh sách hồ sơ đính kèm của một công trình
```sql
SELECT 
    dm.id,
    dm.file_entry_id,
    dm.original_name,
    dm.file_extension,
    dm.file_size,
    dm.local_path,
    dm.uploader_username,
    dm.uploaded_at
FROM document_metadata dm
WHERE dm.asset_record_id = :recordId
ORDER BY dm.uploaded_at DESC;
```
*Index kích hoạt:* `idx_doc_meta_asset`.

---

## 3. Ma trận Đảm bảo Hiệu năng (Performance Guarantee Matrix)

| Mẫu truy vấn | Tần suất | Thời gian phản hồi mục tiêu | Chiến lược Tối ưu hóa |
| :--- | :--- | :--- | :--- |
| **GIS Viewport Query** | Rất cao (khi rê chuột bản đồ) | **$< 20\text{ms}$** | PostGIS R-Tree GiST index trên `asset_geometry.geom`. Giới hạn `LIMIT 1000`. |
| **Filter & Pagination** | Cao (khi tra cứu danh mục) | **$< 50\text{ms}$** | B-Tree Composite Index `(route_code, km_from, km_to)` kết hợp `branch_id`. |
| **Fuzzy Text Search** | Trung bình (khi gõ ô tìm kiếm) | **$< 100\text{ms}$** | Trigram GIN index `name gin_trgm_ops` hỗ trợ tìm kiếm mờ tiếng Việt không khóa bảng. |
| **Recursive Tree** | Trung bình (khi xem chi tiết cầu) | **$< 30\text{ms}$** | B-Tree index trên `parent_id` và đệ quy qua CTE giới hạn độ sâu (Depth $\le 5$). |
| **Aggregated KPI** | Thấp - Trung bình (Dashboard) | **$< 200\text{ms}$** | Index trên `(branch_id, asset_type)` hoặc Materialized View tự động refresh định kỳ. |
