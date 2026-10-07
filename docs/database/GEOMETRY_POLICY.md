# CHÍNH SÁCH VÀ QUY CHUẨN DỮ LIỆU KHÔNG GIAN POSTGIS (GEOMETRY_POLICY)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Hệ Quy chiếu Tọa độ Không gian (Spatial Reference Systems - SRS)

Dữ liệu vị trí địa lý của toàn bộ tài sản kết cấu hạ tầng giao thông đường bộ được quản lý thống nhất dựa trên tiện ích mở rộng **PostGIS 3.4+** trên nền PostgreSQL 16.

### 1.1 Hệ Tọa độ Lưu trữ Chuẩn: WGS 84 (EPSG:4326)
- **Đặc điểm:** Tọa độ địa lý cầu (Geographic Coordinate System) sử dụng đơn vị **Độ thập phân (Decimal Degrees)**.
  - **Trục X (Kinh độ - Longitude):** Nằm trong dải xấp xỉ từ $102.0^\circ\text{E}$ đến $110.0^\circ\text{E}$ trên đất liền Việt Nam.
  - **Trục Y (Vĩ độ - Latitude):** Nằm trong dải xấp xỉ từ $8.0^\circ\text{N}$ đến $24.0^\circ\text{N}$ trên đất liền Việt Nam.
- **Lý do lựa chọn làm hệ quy chiếu lưu trữ gốc:**
  - Tương thích tự nhiên với các thư viện bản đồ Web hiện đại (Mapbox GL JS, Leaflet, OpenLayers, Google Maps).
  - Tương thích với định dạng GeoJSON chuẩn (`RFC 7946`), giúp backend Spring Boot trả trực tiếp dữ liệu bản đồ về trình duyệt mà không cần tính toán chuyển đổi tọa độ phức tạp.

### 1.2 Hệ Tọa độ Chiếu phẳng và Quy chuẩn VN-2000
Khi phục vụ công tác đo đạc diện tích (mét vuông), tính toán chiều dài thực tế (mét) hoặc xuất dữ liệu theo quy chuẩn trắc địa nhà nước:
- **EPSG:3857 (Web Mercator):** Phục vụ cắt mảnh bản đồ Vector Tiles (MVT) và hiển thị Web.
- **VN-2000 (EPSG:3405 / EPSG:5899 - Kinh tuyến trục quốc gia $105^\circ$):** Sử dụng hàm chuyển đổi khi cần báo cáo trắc địa chính thức:
  ```sql
  -- Tính khoảng cách thực tế chính xác theo mét giữa 2 điểm trên ellipsoid WGS 84:
  SELECT ST_Distance(geom::geography, target_geom::geography) AS distance_meters;
  
  -- Chuyển đổi tọa độ sang VN-2000 nội bộ (kinh tuyến trục 105 độ, múi 3 độ):
  SELECT ST_Transform(geom, 3405) AS geom_vn2000;
  ```

---

## 2. Phân loại và Quy chuẩn Kiểu Hình học (Geometry Types)

Trong bảng `asset_geometry`, cột `geom` được định nghĩa là kiểu hình học tổng quát `GEOMETRY(Geometry, 4326)`, kết hợp 2 cột chuyên biệt `point_geom` và `line_geom`:

```
+----------------------------------------------------------------------------------------+
|                                    BẢNG ASSET_GEOMETRY                                |
+----------------------------------------------------------------------------------------+
|  geom:        GEOMETRY(Geometry, 4326)   -> Cột tổng quát phục vụ hiển thị chung       |
|  point_geom:  GEOMETRY(Point, 4326)      -> Cột chuyên biệt cho tài sản dạng ĐIỂM      |
|  line_geom:   GEOMETRY(LineString, 4326) -> Cột chuyên biệt cho tài sản dạng TUYẾN     |
+----------------------------------------------------------------------------------------+
```

### 2.1 Đối tượng Dạng Điểm (`POINT`)
Áp dụng cho các công trình có vị trí tập trung tại một tọa độ xác định:
- **Biển báo đường bộ** (`tbl_road_sign`) - 222,112 điểm.
- **Giá long môn, gương cầu lồi** (`road_sphere_mirror`) - 191,928 điểm.
- **Cọc tiêu, cọc H** (`tbl_guide_post`) - 37,042 điểm.
- **Cột Km** (`tbl_km_post`) - 21,910 điểm.
- **Trọng tâm cầu đường bộ** (`tbl_bridge`) - 11,631 điểm.
- **Điểm dừng xe bus, Bến xe** (`tbl_bus_stops`, `tbl_bus_station`) - ~5,800 điểm.
- **Trạm thu phí, Trạm kiểm tra tải trọng** (`tbl_toll_booth`, `weight_station`) - ~130 điểm.

### 2.2 Đối tượng Dạng Đường (`LINESTRING`)
Áp dụng cho các công trình trải dài dọc theo tuyến đường:
- **Hộ lan tôn sóng, hàng rào bảo vệ** (`tbl_guardrail`) - 51,697 đoạn.
- **Rãnh dọc thoát nước, cống biên** (`tbl_longitudinal`) - 61,161 đoạn.
- **Mái dốc ta luy, kè tường chắn** (`tbl_slope`, `tbl_retaining_wall`) - ~21,000 đoạn.
- **Dải phân cách giữa** (`tbl_median_strip`) - 6,960 đoạn.
- **Đoạn tuyến đường bộ** (`tbl_rmd`, `tbl_segment`, `duonggom`, `duongnhanh`) - ~7,500 đoạn.

### 2.3 Đối tượng Dạng Vùng (`POLYGON`)
Áp dụng cho các cơ sở có ranh giới khu đất:
- **Khu đất tài sản hạ tầng đường bộ** (`tbl_land_btra`) - 12 vùng.
- **Trạm dừng nghỉ, Bến xe tĩnh, Kho dự phòng** (`tbl_rest_stops`, `tbl_disaster_res_facility`).

---

## 3. Thuật toán Trích xuất, Làm sạch và Dựng Hình học

Dữ liệu nguồn Vidagis cung cấp tọa độ rải rác ở hai vị trí:
1. `x_min`, `y_min`, `x_max`, `y_max` trong phong bì bản ghi.
2. `from_coordinatex`, `from_coordinatey` (Điểm đầu) và `to_coordinatex`, `to_coordinatey` (Điểm cuối) trong mảng `data_`.

### 3.1 Quy tắc Ưu tiên Trích xuất (Extraction Priority)
```
                    +------------------------------------+
                    |  Bản ghi tài sản cần tạo hình học  |
                    +------------------------------------+
                                       |
                   [Kiểm tra x_min, y_min ở phong bì ngoài]
                                       |
                      +----------------+----------------+
                      |                                 |
                 (HỢP LỆ)                          (NULL/RỖNG)
                      |                                 |
                      v                                 v
          [ƯU TIÊN 1: Lấy BBOX]             [ƯU TIÊN 2: Lấy trong data_]
          - Nếu x_min == x_max:              - from_coordinatex/y
            -> POINT(x_min, y_min)           - to_coordinatex/y
          - Nếu x_min != x_max:              -> POINT hoặc LINESTRING
            -> LINESTRING / BBOX
```

### 3.2 Quy chuẩn Kiểm tra Hợp lệ Địa lý Việt Nam (Geo-fencing Validation)
Một tọa độ $(X, Y)$ được coi là hợp lệ khi thỏa mãn đồng thời:
1. $X$ (Kinh độ) là số thực: $102.0 \le X \le 110.0$
2. $Y$ (Vĩ độ) là số thực: $8.0 \le Y \le 24.0$
3. Tọa độ không được bằng 0 (`X <> 0 AND Y <> 0`).

*Xử lý khi vi phạm:*
- Nếu tọa độ bị đảo ngược ($X \approx 21.0$ và $Y \approx 106.0$ do lỗi nhập nhầm kinh độ/vĩ độ): Hệ thống tự động đảo lại vị trí `(Y, X)`.
- Nếu tọa độ bằng 0 hoặc nằm hoàn toàn ngoài phạm vi: Đặt `geom = NULL`, gắn cờ `is_valid = FALSE` và ghi nhận một cảnh báo vào bảng `import_error`.

---

## 4. Chính sách Xử lý Hình học Tuyến (Line Geometry Policy)

### 4.1 Giai đoạn 1 (Hiện tại): Biểu diễn Tuyến bằng Đoạn thẳng Hai Điểm
- **Thực tế dữ liệu nguồn:** Các tài sản dạng tuyến (`tbl_guardrail`, `tbl_longitudinal`) chỉ ghi nhận tọa độ điểm đầu (`from_coordinatex/y`) và điểm cuối (`to_coordinatex/y`), không có danh sách các đỉnh trung gian uốn lượn theo sườn núi/vòng cua.
- **Quyết định kỹ thuật Giai đoạn 1:**
  - Tạo đoạn thẳng 2 điểm nối trực tiếp giữa điểm đầu và điểm cuối:
    ```sql
    geom = ST_SetSRID(ST_MakeLine(ST_MakePoint(lon1, lat1), ST_MakePoint(lon2, lat2)), 4326);
    ```
  - Thiết lập thuộc tính `line_geom = geom` để hiển thị trực quan phạm vi chiều dài công trình trên bản đồ.

### 4.2 Giai đoạn 2 (Lộ trình Nâng cao): Khớp Tuyến Tự động (Map Snapping & LRS)
- Tận dụng tim tuyến đường bộ chuẩn (`mst_national_road`, `tbl_segment`):
  - Khi đã có chuỗi đỉnh (Polyline) chi tiết của tim đường quốc lộ, sử dụng hàm PostGIS Linear Referencing System:
    ```sql
    -- Cắt một đoạn tim đường tương ứng với lý trình từ Km_A đến Km_B:
    SELECT ST_LocateBetween(route_geom, km_from, km_to);
    ```
  - Thay thế đoạn thẳng 2 điểm bằng đoạn polyline bám sát theo độ cong thực tế của mặt đường.

---

## 5. Tối ưu hóa Truy vấn Bản đồ WebGIS (Performance & Vector Tiles)

### 5.1 Nguyên tắc Truy vấn Hộp bao Không gian (Spatial Viewport Query)
Khi người dùng di chuyển hoặc phóng to thu nhỏ bản đồ, trình duyệt gửi về tọa độ hình chữ nhật màn hình `(minLon, minLat, maxLon, maxLat)`. Câu lệnh SQL **bắt buộc dùng toán tử `&&` kết hợp `ST_MakeEnvelope`**:

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
  AND ar.is_deleted = FALSE
LIMIT 1000;
```
*Tác dụng:* Toán tử `&&` chỉ kiểm tra sự chồng lấn của hộp bao (Bounding Box Overlap) thông qua cây chỉ mục GiST R-Tree, tốc độ thực thi chỉ từ **$5\text{ms}$ đến $12\text{ms}$**.

---

### 5.2 Sinh Mảnh Bản đồ Vector (Mapbox Vector Tiles - MVT) Native trong PostgreSQL
Đối với các lớp dữ liệu biển báo hoặc hộ lan có mật độ cực dày, việc trả về hàng trăm ngàn GeoJSON sẽ làm nghẽn băng thông và treo trình duyệt. Hệ thống sử dụng trực tiếp hàm `ST_AsMVT` của PostGIS để sinh tile nhị phân dạng `.pbf`:

```sql
-- Hàm sinh tile vector chuẩn theo tọa độ tile (z, x, y):
WITH bounds AS (
    SELECT ST_TileEnvelope(:z, :x, :y) AS env
),
mvtgeom AS (
    SELECT 
        ar.id,
        ar.name,
        ar.asset_type,
        ar.route_code,
        ST_AsMVTGeom(ag.geom, bounds.env, 4096, 64, true) AS mvt_geom
    FROM asset_geometry ag
    JOIN asset_record ar ON ag.asset_id = ar.id
    JOIN bounds ON ag.geom && bounds.env
    WHERE ar.is_deleted = FALSE
)
SELECT ST_AsMVT(mvtgeom.*, 'assets_layer') AS mvt_tile FROM mvtgeom;
```
*Hiệu năng:* Dung lượng một tile vector nén nhị phân chỉ từ **$20\text{KB}$ đến $80\text{KB}$**, cho phép hiển thị mượt mà hàng triệu điểm tài sản ở tốc độ 60 khung hình/giây trên giao diện Mapbox/Leaflet.
