# KIẾN TRÚC DATABASE HẠ TẦNG ĐƯỜNG BỘ (KCHT ĐB)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Bối cảnh và Mục tiêu Hệ thống

Hệ thống quản trị và khai thác dữ liệu kết cấu hạ tầng đường bộ (KCHT ĐB) phục vụ công tác số hóa, tra cứu, hiển thị bản đồ GIS và điều hành nghiệp vụ của Cục Đường bộ Việt Nam (DRVN). 

Bộ dữ liệu nguồn thu thập được từ hệ thống hiện hữu (snapshot ngày 05/10/2026) bao gồm:
- **658 tệp dữ liệu JSON** (và 658 tệp metadata đi kèm) với tổng dung lượng xấp xỉ **3.86 GB**.
- **1,104,088 bản ghi**, bao gồm 57 tập tài sản vật lý (~833,000 bản ghi), 152 danh mục tham chiếu, 123 tệp quản lý hồ sơ tài liệu và 314 tập báo cáo/thống kê định kỳ.
- Hệ thống công nghệ đích quy định:
  - **Cơ sở dữ liệu:** PostgreSQL 16 + Tiện ích mở rộng PostGIS (hỗ trợ không gian địa lý).
  - **Backend:** Java 21 + Spring Boot 3.3+.
  - **Quản lý phiên bản Schema:** Flyway Migration.

### 1.1 Vấn đề then chốt: Tránh bùng nổ Schema (Anti-pattern 658 Bảng cứng)
Trong hệ thống cũ hoặc khi crawler trích xuất, mỗi endpoint tương ứng với 1 file JSON (658 files). Nếu tạo 658 bảng vật lý riêng biệt trong PostgreSQL:
- **Bùng nổ đối tượng cơ sở dữ liệu:** Gây quá tải bộ nhớ đệm danh mục (`pg_class`, `pg_attribute`), khó khăn bảo trì, kiểm toán và giám sát hiệu năng.
- **Khóa DDL và bất khả thi khi truy vấn liên kết:** Không thể viết câu lệnh tìm kiếm toàn cục (Global Search) hoặc lọc tài sản theo cung đường, địa bàn khi tài sản bị phân mảnh trên 57 bảng tài sản và hàng trăm bảng báo cáo phụ.
- **Thực tế dữ liệu nguồn:** Hơn 85% trường thông tin của các loại tài sản tuân theo một khuôn mẫu phong bì chung (Envelope Pattern: `gid`, `id`, `tableid`, `fielddisplay`, `parent_id`, `state`, `branch_id`, tọa độ) và các thuộc tính động được lưu trong mảng `data_`.

---

## 2. Kiến trúc Hai lớp (Two-Tier Hybrid Architecture)

Hệ thống áp dụng kiến trúc hai lớp kết hợp giữa **Hồ dữ liệu quan hệ (Relational Ingestion Lake)** và **Kho vận hành số liệu chuẩn hóa (Curated Operational Data Store)**:

```
+---------------------------------------------------------------------------------------+
|                                    NGUỒN DỮ LIỆU JSON                                 |
|         658 JSON Files (~3.86 GB, 1.1M bản ghi) + 309 Binary Document Files           |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+=======================================================================================+
|                     LỚP 1: RAW INGESTION (Lưu trữ nguyên bản ELT)                     |
|                                                                                       |
|  - raw_dataset_record: Lưu trữ nguyên bản JSONB, dataset_code, record_key, hash,      |
|                        nguồn gốc, trạng thái phân tích.                               |
|  - import_job / import_file / import_error: Giám sát toàn trình tiến trình import.    |
|  - dataset_registry / dataset_field: Lưu trữ từ điển dữ liệu (10,142 metadata fields).|
+=======================================================================================+
                                           |
                         [Trích xuất & Chuẩn hóa ETL/ELT]
                                           |
                                           v
+=======================================================================================+
|                 LỚP 2: CURATED / OPERATIONAL LAYER (Dữ liệu Vận hành)                 |
|                                                                                       |
|  [HẠ TẦNG TÀI SẢN UNIFIED]                                                            |
|  - asset_record:        Các trường định danh, phân cấp, tuyến, lý trình, địa giới,    |
|                         trạng thái bảo trì và thuộc tính mở rộng JSONB.               |
|  - asset_geometry:      Tọa độ PostGIS (Point, LineString), SRID 4326, Spatial Index. |
|                                                                                       |
|  [DANH MỤC & TÀI LIỆU]                                                                |
|  - reference_catalog:   152 danh mục chuẩn hóa (cấp đường, loại vật liệu, biển báo).  |
|  - document_folder:     Cây thư mục hồ sơ hoàn công, tài liệu kỹ thuật.              |
|  - document_metadata:   Chỉ mục 309 tệp nhị phân, liên kết chặt chẽ tới asset_record. |
|                                                                                       |
|  [BẢO MẬT & QUẢN TRỊ]                                                                 |
|  - app_user / app_role / app_permission / audit_log: RBAC & Nhật ký tác động.         |
+=======================================================================================+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                         APPLICATION LAYER (Spring Boot 3 + Java 21)                   |
|     - REST APIs / WebGIS Map Services (GeoJSON / Vector Tiles)                        |
|     - Multi-faceted Search, Filter & Aggregated Dashboard                             |
+---------------------------------------------------------------------------------------+
```

### 2.1 Lớp Raw Ingestion (ELT Landing Zone)
- **Mục đích:** Đảm bảo tính toàn vẹn 100% dữ liệu gốc, hỗ trợ nạp dữ liệu phi cấu trúc cực nhanh, cho phép chạy lại (replay/re-index) khi nghiệp vụ trích xuất thay đổi mà không cần tải lại từ file nguồn.
- **Đặc điểm kỹ thuật:**
  - Bảng `raw_dataset_record` lưu trữ toàn bộ payload bản ghi dưới dạng `jsonb`.
  - Khóa tổng hợp `(dataset_code, record_key)` bảo đảm tính duy nhất và hỗ trợ cơ chế nạp lũy tiến (Upsert).
  - Băm `payload_hash` (SHA-256) giúp phát hiện bản ghi trùng lặp hoặc bản ghi thay đổi để bỏ qua xử lý không cần thiết.
  - Quản lý nhật ký import qua `import_job`, `import_file` và bảng `import_error` để kiểm soát lỗi từng dòng.

### 2.2 Lớp Curated / Operational Layer (Vận hành & Truy vấn)
- **Mục đích:** Tối ưu hóa hiệu năng truy vấn cho giao diện Web, biểu đồ Dashboard và bản đồ số GIS.
- **Nguyên tắc phân định Cột Typed vs Cột JSONB:**
  - **Cột Typed (Cột có kiểu dữ liệu tường minh):**
    - Các trường định danh: `id` (text), `gid` (bigint).
    - Các trường phân loại và quan hệ: `dataset_code`, `asset_type`, `parent_id`.
    - Các trường không gian & tuyến: `route_code`, `km_from`, `km_to`, `lytrinh`.
    - Các trường hành chính & phân quyền: `province_id`, `district_name`, `town_name`, `organization_id`, `branch_id`.
    - Các trường vòng đời & trạng thái: `state`, `status_id`, `construction_year`, `maintain_value`.
  - **Cột JSONB (`attributes`):**
    - Chứa hàng trăm trường đặc thù của từng loại công trình (ví dụ: mố cầu, rãnh dọc, hộ lan, thiết bị trong hầm). Các trường này không dùng để phân quyền toàn cục và không đồng nhất giữa các tài sản.
- **Tách biệt Không gian (`asset_geometry`):**
  - Quản lý hình học PostGIS theo chuẩn `EPSG:4326` (WGS 84).
  - Tách riêng bảng hình học giúp bảng `asset_record` gọn nhẹ khi thực hiện các truy vấn bảng biểu thông thường, đồng thời cho phép tạo chỉ mục không gian chuyên biệt `GiST`.

---

## 3. Ngăn xếp Công nghệ và Quyết định Thiết kế

### 3.1 PostgreSQL 16 + PostGIS
- **Kiểu dữ liệu JSONB tiên tiến:** Tận dụng khả năng tối ưu lưu trữ nhị phân, toán tử truy vấn path (`@>`, `jsonb_extract_path_text`) và chỉ mục GIN.
- **PostGIS 3.4+:**
  - Sử dụng hệ tọa độ chuẩn toàn cầu `EPSG:4326` (WGS 84) phù hợp với Mapbox/Leaflet/OpenLayers trên Web.
  - Hỗ trợ hàm không gian hiệu năng cao: `ST_Intersects`, `ST_DWithin`, `ST_MakeEnvelope`, `ST_ClusterKMeans`.
  - Hỗ trợ sinh trực tiếp GeoJSON/MVT (Mapbox Vector Tiles) từ tầng cơ sở dữ liệu (`ST_AsMVT`, `ST_AsGeoJSON`).

### 3.2 Java 21 + Spring Boot 3.3
- **Virtual Threads (Project Loom):** Xử lý đồng thời khối lượng lớn các request truy vấn dữ liệu bản đồ và danh sách tài sản mà không gây nghẽn luồng.
- **Spring Data JPA & Hibernate 6:** Tận dụng hỗ trợ native cho PostgreSQL JSONB (`@JdbcTypeCode(SqlTypes.JSON)`) và PostGIS (`GeometryType`).
- **Spring Batch / Concurrent Processing:** Phục vụ tiến trình ETL nạp dữ liệu từ Raw sang Curated theo từng batch kích thước 1,000 - 5,000 bản ghi.

### 3.3 Cơ chế Migration với Flyway
- Tổ chức schema phân tầng:
  - `V1__init_security_and_system.sql`: Khởi tạo bảng người dùng, phân quyền, cấu hình hệ thống.
  - `V2__init_raw_ingestion.sql`: Khởi tạo bảng registry, raw records và logging.
  - `V3__init_reference_and_documents.sql`: Khởi tạo bảng danh mục và hồ sơ tài liệu.
  - `V4__init_operational_assets.sql`: Khởi tạo bảng tài sản, hình học PostGIS và chỉ mục.
  - `R__operational_views_and_functions.sql`: Repeatable migrations cho các hàm tính toán, view báo cáo động.

---

## 4. Ước tính Kích thước và Tài nguyên Lưu trữ

Dựa trên phân tích 658 tệp JSON (3.86 GB thô):

| Phân vùng dữ liệu | Số lượng bản ghi ước tính | Kích thước dữ liệu | Kích thước Index ước tính | Tổng dung lượng dự kiến |
| :--- | :--- | :--- | :--- | :--- |
| **Raw Ingestion Layer** (`raw_dataset_record`) | 1,104,088 | ~3.9 GB | ~800 MB (B-Tree + Hash) | **~4.7 GB** |
| **Curated Asset Layer** (`asset_record`) | ~833,000 | ~1.4 GB | ~600 MB (B-Tree + GIN) | **~2.0 GB** |
| **Spatial Layer** (`asset_geometry`) | ~833,000 | ~250 MB | ~180 MB (GiST) | **~430 MB** |
| **Reference Catalogs** (`reference_catalog`) | 6,373 | ~5 MB | ~3 MB | **~8 MB** |
| **Documents & Folders** (`document_metadata`) | 309 files / 121 folders | ~2 MB | ~1 MB | **~3 MB** |
| **Security & Logging** (`audit_log`, ...) | Tăng trưởng theo vận hành | ~100 MB ban đầu | ~50 MB | **~150 MB** |
| **TỔNG CỘNG BAN ĐẦU** | **~1.1 triệu bản ghi** | **~5.6 GB** | **~1.6 GB** | **~7.3 GB** |

### Khuyến nghị Cấu hình PostgreSQL 16:
- `shared_buffers = 4GB` (dành cho server 16GB RAM)
- `work_mem = 64MB`
- `maintenance_work_mem = 1GB`
- `effective_cache_size = 12GB`
- `random_page_cost = 1.1` (tối ưu cho ổ đĩa SSD NVMe)

---

## 5. Nguyên tắc Toàn vẹn và Phân vùng Vận hành
1. **Không can thiệp sửa đổi dữ liệu gốc trong Raw:** Tầng raw luôn bảo lưu chính xác những gì crawl được từ hệ thống cũ. Mọi logic chuẩn hóa (xóa thẻ `<span>`, ép kiểu số, chuẩn hóa mã tỉnh) được thực hiện trong quá trình chuyển dịch lên Curated.
2. **Loại trừ dữ liệu không phù hợp khỏi Curated:**
   - 148 tập báo cáo chi tiết (`maintenance_detail_*_chitiet`) và 148 tập báo cáo tổng hợp (`maintenance_detail_*_tonghop`) không được tạo thành các bảng cứng mà được thay thế bằng Dynamic Query / View trên `asset_record`.
   - Bảng tài liệu hướng dẫn (`user_guide_tables`, 1,620 dòng) chỉ lưu ở tầng tài liệu tham khảo/hồ sơ, không đưa vào danh mục tài sản hạ tầng đường bộ.
   - Các file rỗng (195 files 0 dòng) chỉ ghi nhận trạng thái trong `dataset_registry` để hoàn chỉnh audit log, không cấp phát không gian trong bảng curated.
