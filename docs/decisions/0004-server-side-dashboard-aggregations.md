# 0004. TỔNG HỢP DỮ LIỆU SERVER-SIDE VÀ BẢNG ĐIỀU HÀNH KCHT ĐƯỜNG BỘ

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Hệ thống quản lý KCHT Đường bộ lưu trữ quy mô dữ liệu rất lớn:
- **658 tập dữ liệu** (57 tập tài sản vật thể, 601 danh mục / bảng tham chiếu).
- **1.102.397 bản ghi** trong bảng `raw_dataset_record` (trong đó riêng biển báo `tbl_road_sign` chiếm 222.112 biển, cầu đường bộ `tbl_bridge` chiếm 11.631 cầu, tuyến đường `mst_national_road` dài hơn 27.469 km).

### Yêu cầu cốt lõi của Giai đoạn 7:
1. **Server-side Aggregate APIs:** Toàn bộ việc tổng hợp số liệu (tổng tài sản, phân bổ theo 4 Khu Quản lý Đường bộ & Sở GTVT địa phương, phân loại biển báo theo QCVN 41:2019/BGTVT, thống kê chiều dài tuyến quốc lộ và tài sản mới nạp) phải được thực hiện tại database/server-side.
2. **Quy tắc Nghiêm ngặt về Hiệu năng Client:** **Tuyệt đối không tính tổng hàng trăm nghìn dòng trong trình duyệt (Browser)**. Trình duyệt chỉ nhận các chỉ số tổng hợp gọn nhẹ qua DTO.
3. **Quy ước Minh bạch Dữ liệu (Audit & Lineage Requirement):** **Mỗi Card / Thành phần hiển thị phải bắt buộc ghi rõ 4 trường thông tin:**
   - **Tập dữ liệu nguồn (Source Dataset)**: Định danh bảng / view gốc (ví dụ: `tbl_bridge`, `tbl_road_sign`, `mst_national_road`, `mv_dashboard_branch_stats`).
   - **Bộ lọc áp dụng (Filter Applied)**: Tiêu chí lọc dữ liệu (ví dụ: `Toàn quốc - Đang khai thác`, `QCVN 41:2019/BGTVT`, `Theo Chi nhánh`).
   - **Thời điểm cập nhật (Last Updated)**: Mốc thời gian dữ liệu được tổng hợp gần nhất.
   - **Đường dẫn sang danh sách chi tiết (Link)**: Nút hành động dẫn trực tiếp sang bảng tra cứu chi tiết (`/assets`, `/reports`, `/documents`).
4. **Giao diện chuẩn hóa:** Sử dụng React + Vite + TypeScript + Ant Design 5 + TanStack Query.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (ARCHITECTURAL DECISIONS)

```
+---------------------------------------------------------------------------------------------------+
|                                          FRONTEND (REACT + VITE)                                   |
|  DashboardPage (TanStack Query Cache + Ant Design 5)                                              |
|  - 6 KPI Metric Cards (Source + Filter + Timestamp + Link)                                        |
|  - Tab 1: Phân bổ theo 4 Khu QLĐB & Sở GTVT (mv_dashboard_branch_stats)                           |
|  - Tab 2: Chuyên đề Biển báo Giao thông QCVN 41 (tbl_road_sign)                                    |
|  - Tab 3: Thống kê Tuyến & Chiều dài Quốc lộ (mst_national_road - 27.469 km)                      |
|  - Tab 4: Top 10 Dataset Lớn nhất & Feed 10 Tài sản Nạp Mới nhất                                 |
+-------------------------------------------------+-------------------------------------------------+
                                                  | REST API (/api/dashboard/*)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                          BACKEND (SPRING BOOT 3.3.4)                              |
|  DashboardApiController                                                                           |
|  DashboardService (Spring 6 JdbcClient, Sub-millisecond Execution, Branch Name Normalizer)        |
+-------------------------------------------------+-------------------------------------------------+
                                                  | SQL Execution
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                    DATABASE (POSTGRESQL 16 + POSTGIS)                             |
|  - Materialized View: mv_dashboard_branch_stats (Aggregate 1.1M rows -> 37 rows, index unique)   |
|  - Standard View: view_dashboard_national_road_stats (168 routes, actual_length parsing)         |
|  - Indexes: idx_raw_dataset_branch, idx_raw_dataset_kind, idx_raw_dataset_imported               |
|  - Concurrent Refresh Procedure: refresh_dashboard_aggregates()                                   |
+---------------------------------------------------------------------------------------------------+
```

### Quyết định 1: Migration Cơ sở Dữ liệu V7 với Materialized View & Specialized Indexes
- Thêm migration `V7__dashboard_aggregations.sql`:
  - Tạo chỉ mục phức hợp `idx_raw_dataset_branch` trên `raw_dataset_record (dataset_key, (raw_payload->>'branch_id'))` phục vụ nhóm theo đơn vị.
  - Tạo Materialized View `mv_dashboard_branch_stats`:
    - Rút gọn 1,1 triệu bản ghi thành 37 dòng thống kê phân bổ theo Chi nhánh/Đơn vị.
    - Tổng hợp nhanh số cầu (`tbl_bridge`), số biển báo (`tbl_road_sign`) và tổng tài sản vật thể.
    - Đánh Unique Index `uq_mv_dashboard_branch ON mv_dashboard_branch_stats(branch_id)` cho phép làm mới đồng thời trong nền: `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
  - Tạo View `view_dashboard_national_road_stats`:
    - Bóc tách trường JSON `data_->'actual_length'` của 168 tuyến quốc lộ chính.
    - Tính toán chính xác tổng chiều dài thực tế: **27.469,26 km**.
  - Tạo thủ tục lưu trữ `refresh_dashboard_aggregates()` để kích hoạt tự động theo lịch cron hoặc sau mỗi mẻ import lớn.

### Quyết định 2: Bộ DTO Thống kê Đầy đủ Metadata
Tất cả 6 DTO backend đều bắt buộc đóng gói 4 trường truy nguyên nguồn gốc:
1. `DashboardSummaryDto`: Tổng hợp KPI cấp điều hành (830.836 tài sản, 11.631 cầu, 222.112 biển báo, 168 tuyến quốc lộ, 27.469,26 km, 658 datasets, 309 hồ sơ MinIO).
2. `DashboardBranchStatDto`: Thống kê phân bổ theo 4 Khu QLĐB (`kqldb_1` đến `kqldb_4`) và các Sở GTVT địa phương (`sxd_*`).
3. `DashboardRoadSignStatDto`: Thống kê chuyên đề biển báo theo đơn vị và theo nhóm quy chuẩn QCVN 41:2019/BGTVT (Biển tam giác cảnh báo nguy hiểm, Biển tròn cấm/hiệu lệnh, Biển chữ nhật chỉ dẫn, Biển phụ).
4. `DashboardRoadLengthStatDto`: Thống kê chiều dài mạng lưới đường bộ, Top 10 tuyến dài nhất (QL.1 dài 3.878,94 km, QL.Hồ Chí Minh dài 2.661,92 km...) và phân bổ cự ly theo 5 khoảng cách (<50km, 50-100km, 100-200km, 200-500km, >500km).
5. `DashboardDatasetStatDto`: Top 10 tập dữ liệu vật thể quy mô lớn nhất hệ thống.
6. `DashboardRecentAssetDto`: Nhật ký nạp / cập nhật 10 tài sản gần nhất kèm nhãn thời gian thực và link tra cứu.

### Quyết định 3: Tối ưu Truy vấn với Spring 6 JdbcClient
- Thay vì nạp các Entity JPA nặng nề, `DashboardService` sử dụng `JdbcClient` để thực thi truy vấn trực tiếp xuống view/materialized view và metadata bảng.
- **Tránh bẫy kỹ thuật:**
  - Không sử dụng `singleMap()` (không tồn tại trong Spring 6 JdbcClient); dùng `singleRow()` trả về `Map<String, Object>`.
  - Đặt bí danh cột tường minh (`AS total_routes`, `AS total_length_km`) để tránh trường hợp JDBC trả về tên cột hàm mặc định của PostgreSQL (`round`, `coalesce`).
  - Toàn bộ 6 API phản hồi chỉ trong **1 - 5 mili-giây**.

### Quyết định 4: Chuẩn hóa Tên Đơn vị (Branch Name Resolver)
Hệ thống JSON gốc chứa các mã chi nhánh viết tắt (`kqldb_1`, `kqldb_2`, `kqldb_3`, `kqldb_4`, `sxd_hanoi`, `sxd_hcm`...). Backend tích hợp bộ chuyển đổi chuẩn hóa:
- `kqldb_1` $\rightarrow$ "Khu Quản lý Đường bộ I"
- `kqldb_2` $\rightarrow$ "Khu Quản lý Đường bộ II"
- `kqldb_3` $\rightarrow$ "Khu Quản lý Đường bộ III"
- `kqldb_4` $\rightarrow$ "Khu Quản lý Đường bộ IV"
- `sxd_<province>` $\rightarrow$ "Sở GTVT <Tỉnh/Thành phố>"

### Quyết định 5: Thiết kế Giao diện Frontend Tuân thủ Tuyệt đối Data Lineage
- Xây dựng component chuẩn hóa `DashboardKpiCard` và `SectionCardWrapper` trong `DashboardPage.tsx`:
  - Mỗi thẻ hiển thị rõ ràng huy hiệu Nguồn (`Tag color="blue"`), huy hiệu Lọc (`Tag color="default"`), thời điểm cập nhật (`ClockCircleOutlined`), và nút dẫn trực tiếp tới dữ liệu chi tiết (`Button type="link"`).
  - Tích hợp TanStack Query với `staleTime: 60s`, cho phép tải dữ liệu song song 6 endpoint và hỗ trợ nút "Làm mới Dữ liệu" quay động mượt mà.
  - Sử dụng Ant Design 5 (Tabs, Tables, Progress, Badges, Statistics) hiển thị trực quan tỷ trọng % mà không cần cài thêm thư viện biểu đồ bên ngoài.

---

## 3. KẾT QUẢ VÀ BẰNG CHỨNG KIỂM THỬ (VERIFICATION EVIDENCE)

### Backend Test Suite:
- Đã chạy: `.\mvnw.bat test`
- Kết quả: **69/69 tests PASS (BUILD SUCCESS)**:
  - `DashboardApiControllerIntegrationTest`: 7/7 tests kiểm thử toàn diện quyền truy cập (yêu cầu xác thực JWT), kiểm thử nội dung summary, branch stats, road sign stats, road length stats, datasets, recent assets, và kiểm tra metadata (source dataset, filter applied, detailUrl, lastUpdated).

### Frontend Test Suite & Production Build:
- Đã chạy: `npm test` trong `frontend/`
- Kết quả: **4/4 test suites PASS, 20/20 tests PASS**:
  - `DashboardPage.test.tsx` (7/7 tests):
    1. Renders welcome banner and server-side aggregation notice.
    2. Renders all 6 executive KPI cards with source, filter, and detail links.
    3. Renders branch distribution table with aggregated metrics.
    4. Switches to Road Signs tab and displays QCVN 41 categories and branch breakdowns.
    5. Switches to Road Lengths tab and displays longest routes and distance distribution.
    6. Switches to Datasets & Recent Assets tab and displays recent feed.
    7. Triggers refresh when "Làm mới Dữ liệu" button is clicked.
- Đã chạy: `npm run build` trong `frontend/`
- Kết quả: **Build thành công không lỗi TypeScript nào (`tsc -b && vite build` xong trong 14.77s)**.

---

## 4. HƯỚNG DẪN VẬN HÀNH VÀ LÀM MỚI DỮ LIỆU (OPERATIONAL GUIDE)

Khi có đợt nạp dữ liệu mới từ import worker:
1. Chạy thủ tục làm mới Materialized View:
   ```sql
   SELECT refresh_dashboard_aggregates();
   ```
2. Frontend tự động cập nhật số liệu mới sau khi cache TanStack Query hết hạn (60 giây) hoặc khi cán bộ nhấn nút "Làm mới Dữ liệu" trên thanh công cụ.
