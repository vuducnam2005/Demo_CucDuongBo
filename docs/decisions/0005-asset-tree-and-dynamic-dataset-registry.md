# 0005. CÂY DANH MỤC TÀI SẢN VÀ BẢNG DỮ LIỆU ĐỘNG THEO DATASET REGISTRY

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Hệ thống KCHT Đường bộ lưu trữ danh mục đồ sộ gồm **658 tập dữ liệu**:
- **57 tập dữ liệu tài sản vật thể (physical assets):** Biển báo (`tbl_road_sign`), cầu đường bộ (`tbl_bridge`), mạng lưới quốc lộ (`mst_national_road`), hầm đường bộ, cống thoát nước, kè, trạm thu phí,...
- **601 tập dữ liệu danh mục & tham chiếu (reference catalogs & modules):** Đơn vị quản lý, địa giới hành chính, phân loại kỹ thuật, quy chuẩn đường bộ.
- Quy mô bản ghi: Hơn 1.100.000 bản ghi, trong đó `tbl_road_sign` có 222.112 biển báo, `tbl_bridge` có 11.631 cầu.

### Thách thức Kỹ thuật:
1. **Không thể viết tĩnh 658 trang quản lý riêng biệt:** Cần kiến trúc dữ liệu hướng metadata (metadata-driven UI) để tự động sinh cột, thuộc tính và cấu hình tra cứu dựa vào `dataset_registry`.
2. **Quy tắc Nghiêm ngặt về Hiệu năng:** Tuyệt đối không tải hàng trăm nghìn dòng vào trình duyệt cùng lúc; toàn bộ lọc (`q`, `branch_id`, `state_name`), sắp xếp (`sort`), phân trang (`page`, `size`) phải được xử lý tại server qua SQL PostGIS.
3. **Cây danh mục phân cấp & Lazy-loading:** Điều hướng trực quan theo 5 nhóm tài sản trọng yếu, hỗ trợ nạp lười (lazy-load) cho hơn 600 danh mục mở rộng khi người dùng mở rộng nhánh.
4. **Phân quyền thao tác theo vai trò (RBAC):** Chỉ cho phép `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EDITOR` chọn nhiều dòng để xử lý (bulk action/gắn cờ kiểm tra); tài khoản `ROLE_VIEWER` chỉ được tra cứu (read-only).
5. **Xuất báo cáo định dạng CSV chuẩn tiếng Việt:** Hỗ trợ xuất dữ liệu theo đúng bộ lọc và từ khóa tìm kiếm đang áp dụng, có UTF-8 Byte Order Mark (`\uFEFF`) để Microsoft Excel hiển thị đúng dấu tiếng Việt không bị lỗi font (mojibake).
6. **Liên kết không gian GIS:** Bản ghi có tọa độ (`x_min/y_min` hoặc `longitude/latitude`) phải có nút mở trực tiếp trên bản đồ số WebGIS với tọa độ và mã định danh.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

```
+---------------------------------------------------------------------------------------------------+
|                                          FRONTEND (REACT + VITE)                                   |
|  AssetListPage (Ant Design 5 + TanStack Query)                                                    |
|  ├── Left Column: Cây Phân Cấp Dữ liệu (Tree Lazy-loading, Bộ chọn nhanh 3 Vertical Slices)      |
|  └── Right Column: Bảng Dữ liệu Động                                                               |
|       ├── Header Metadata & Toolbar (Total records, Tệp nguồn, Nút Xuất CSV, Mở WebGIS)           |
|       ├── Form Lọc & Tìm kiếm Server-side (Từ khóa q, Đơn vị quản lý branch_id, Sắp xếp an toàn)   |
|       ├── Bảng Antd Table (Dynamic Columns, Bulk Selection cho Admin/Editor, Paging Server-side)  |
|       └── Drawer Chi tiết Bản ghi (4 Tabs: Thuộc tính data_, Tọa độ GIS, JSON Gốc, Audit Log)    |
+-------------------------------------------------+-------------------------------------------------+
                                                  | REST API (/api/datasets/*)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                          BACKEND (SPRING BOOT 3.3.4)                              |
|  DatasetApiController:                                                                            |
|  ├── GET /api/datasets/tree?parent={key}          -> DatasetQueryService.getDatasetTree()          |
|  ├── GET /api/datasets/{dataset}/metadata         -> DatasetQueryService.getDatasetMetadata()      |
|  ├── GET /api/datasets/{dataset}/records          -> DatasetQueryService.queryRecords()            |
|  ├── GET /api/datasets/{dataset}/records/{id}     -> DatasetQueryService.getRecordDetail()         |
|  └── GET /api/datasets/{dataset}/export           -> DatasetQueryService.exportRecordsToCsv()      |
+-------------------------------------------------+-------------------------------------------------+
                                                  | SQL Execution (JdbcClient, PostGIS, JSONB)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                    DATABASE (POSTGRESQL 16 + POSTGIS)                             |
|  - dataset_registry (658 datasets, metadata, source_file, fields_json)                           |
|  - raw_dataset_record (1.1M records, raw_payload JSONB, geom PostGIS Geometry)                   |
|  - Indexes: idx_raw_dataset_branch, idx_raw_dataset_imported, idx_raw_dataset_record_key           |
+---------------------------------------------------------------------------------------------------+
```

### Quyết định 1: Cấu trúc Cây Dữ liệu Phân cấp và Lazy-Loading (`/api/datasets/tree`)
- Backend cung cấp DTO `DatasetTreeNodeDto` gồm `key`, `title`, `datasetKey`, `totalRecords`, `kind`, `icon`, `isLeaf`, `children`.
- Đánh dấu tường minh `@JsonProperty("isLeaf")` trên cả field và getter để ngăn ngừa quy ước JavaBean của Jackson cắt ngắn tên trường thành `leaf`.
- Nút gốc trả về 5 nhóm danh mục trọng yếu (Tuyến quốc lộ, Cầu đường bộ, Biển báo hiệu, Công trình phụ trợ, Đô thị & nút giao) nạp sẵn lá vertical slice, cùng 2 nhóm nút động lazy-loaded:
  - `group_all_assets`: Nạp toàn bộ 57 tập dữ liệu tài sản vật thể khi mở rộng.
  - `group_modules`: Nạp 601 tập dữ liệu tham chiếu danh mục phân loại khi mở rộng.

### Quyết định 2: Bảng Dữ liệu Động (Dynamic Column Generator)
- Cột cơ bản cố định: STT, Mã/Tên hiển thị (`fielddisplay`/`name`), Đơn vị quản lý (chuẩn hóa tên tiếng Việt qua `resolveBranchName`), Trạng thái hoạt động (`state_name`).
- Cột đặc thù theo vertical slice:
  - `tbl_bridge`: Lý trình tim cầu, Chiều dài (m), Chiều rộng (m), Loại kết cấu.
  - `tbl_road_sign`: Mã biển QCVN 41, Nhóm biển báo, Lý trình vị trí, Loại cột biển báo.
  - `mst_national_road`: Mã tuyến đường, Điểm đầu tuyến, Điểm cuối tuyến, Chiều dài thực tế (km).
  - Các tập dữ liệu khác: Tự động trích xuất 3 trường thông tin đầu tiên từ `metadata.fields`.
- Cột không gian GIS: Hiển thị trạng thái tọa độ PostGIS; nếu có tọa độ, cho phép mở nhanh bản đồ WebGIS với tham số `?dataset={key}&id={recordKey}&lat={lat}&lng={lng}`.
- Cột thao tác: Nút mở Drawer chi tiết bản ghi.

### Quyết định 3: Drawer Chi tiết Bản ghi Đa tầng (Record Detail Drawer)
Drawer bên phải rộng 680px, tổ chức thành 4 Tab chuyên biệt:
1. **Thuộc tính Nghiệp vụ:** Hiển thị mã bản ghi, tên, đơn vị, trạng thái, cơ quan thành lập và toàn bộ bảng từ điển thuộc tính kỹ thuật trích xuất từ mảng `raw_payload->'data_'` (tự động loại bỏ thẻ HTML thô).
2. **Không gian & Tọa độ GIS:** Hiển thị hệ quy chiếu PostGIS EPSG:4326 (WGS 84), kinh độ X, vĩ độ Y, khung bao BBOX (`x_min, y_min, x_max, y_max`) và nút điều hướng tới bản đồ số WebGIS.
3. **Dữ liệu Gốc JSON:** Trình xem JSON có định dạng thụt dòng, hỗ trợ nút "Sao chép JSON" vào clipboard.
4. **Kiểm toán & Nguồn gốc:** ID hệ thống CSDL, tập dữ liệu gốc, trạng thái bản ghi và thời điểm nạp vào kho dữ liệu.

### Quyết định 4: Xuất Báo cáo CSV Streaming Tương thích Excel
- Phương thức `exportRecordsToCsv`: Tạo luồng stream CSV trực tiếp ra phản hồi HTTP.
- Ghi 3 byte Byte Order Mark (`\uFEFF`, tức `0xEF, 0xBB, 0xBF`) ở đầu tệp để Microsoft Excel tự động nhận diện UTF-8 không lỗi font tiếng Việt.
- Bọc dấu ngoặc kép và escape các trường có dấu phẩy hoặc dấu ngoặc kép theo chuẩn RFC 4180.
- Áp dụng đầy đủ bộ lọc đang kích hoạt (`q`, `branch_id`, `sort`). Giới hạn tối đa 2.000 dòng để bảo vệ RAM server.

### Quyết định 5: Phân quyền Thao tác Bulk Action theo Vai trò (RBAC)
- Kiểm tra quyền truy cập thông qua `canBulkAction`:
  - `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EDITOR`: Kích hoạt cột hộp kiểm (`rowSelection`) của Ant Design Table, cho phép chọn hàng loạt bản ghi, hiển thị thanh tác vụ thông báo số lượng đã chọn, hỗ trợ xuất các dòng đã chọn hoặc đánh dấu kiểm tra thực địa.
  - `ROLE_VIEWER`: Vô hiệu hóa `rowSelection`, hiển thị thông báo ghi chú tài khoản ở chế độ chỉ đọc.

---

## 3. CÁC PHƯƠNG ÁN ĐÃ CÂN NHẮC VÀ ĐÁNH ĐỔI (TRADE-OFFS)

| Phương án | Ưu điểm | Nhược điểm | Quyết định chọn |
|-----------|---------|------------|-----------------|
| **1. Cây danh mục tải toàn bộ (Full Eager Load)** | Tìm kiếm cây ở client nhanh | Tải đồng thời 658 nút gây chậm mạng và DOM cồng kềnh | **Bác bỏ:** Dùng mô hình nạp trước 5 nhánh trọng yếu + Lazy-loading khi mở rộng nhánh lớn. |
| **2. Tạo 658 component trang tĩnh riêng** | Tùy biến sâu từng thuộc tính | Chi phí bảo trì khổng lồ, trùng lặp mã | **Bác bỏ:** Dùng Dynamic Column Generator kết hợp Vertical Slice chuyên biệt cho 3 tập cốt lõi. |
| **3. Xuất file bằng thư viện Apache POI Excel (.xlsx)** | Định dạng bảng biểu có màu sắc | Tốn nhiều CPU & bộ nhớ heap server khi xuất dữ liệu lớn | **Bác bỏ:** Dùng CSV Streaming với UTF-8 BOM nhẹ, tức thời, tương thích 100% với Excel tiếng Việt. |
| **4. Phân trang và lọc client-side** | Chuyển trang tức thì sau khi tải xong | Tải 222.112 biển báo làm tràn RAM trình duyệt (>500MB) | **Bác bỏ:** 100% phân trang, tìm kiếm, lọc và sắp xếp thực thi server-side qua PostgreSQL. |

---

## 4. KẾT QUẢ VÀ BẰNG CHỨNG XÁC MINH (VERIFICATION)

1. **Backend Integration Tests:**
   - Tạo bộ test tích hợp `DatasetApiTreeAndExportIntegrationTest.java`:
     - `testDatasetTree_Root_ReturnsExpectedNodes()`: Xác minh cấu trúc nút gốc và thuộc tính `isLeaf`.
     - `testDatasetTree_LazyLoad_AllAssets()`: Xác minh nạp lười 50+ tập tài sản vật thể.
     - `testDatasetExport_Csv_HasBomAndHeaders()`: Xác minh xuất CSV có UTF-8 BOM `\uFEFF`.
     - `testDatasetExport_WithKeywordFilter()`: Xác minh bộ lọc từ khóa trên luồng xuất file.
   - Kết quả: **73/73 tests backend vượt qua 100% (0 failures, 0 errors)**.

2. **Frontend Unit & Integration Tests:**
   - Tạo bộ test tích hợp `AssetListPage.test.tsx` (6 kịch bản kiểm thử):
     - Renders tree navigation and vertical slice shortcut buttons.
     - Renders records table with dynamic columns for tbl_bridge.
     - Enables bulk row selection for ROLE_ADMIN and shows selection bar.
     - Disables bulk selection for ROLE_VIEWER (read-only mode).
     - Opens record detail drawer with attributes and coordinates on "Chi tiết" click.
     - Triggers CSV export when export button is clicked.
   - Kết quả: **26/26 tests frontend vượt qua 100% trên cả 5 file test suite**.

3. **Kiểm tra Biên dịch (Build Verification):**
   - Lệnh `npm run build` (`tsc -b && vite build`): Vượt qua thành công, 0 lỗi TypeScript linting.
