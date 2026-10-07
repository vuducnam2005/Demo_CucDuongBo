# DANH MỤC CÂU HỎI MỞ VÀ GIẢ ĐỊNH THIẾT KẾ (OPEN_QUESTIONS)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  
**Nguyên tắc áp dụng:** Mọi điểm chưa chắc chắn phải được ghi lại tại đây để xin ý kiến chủ đầu tư/người dùng thay vì tự suy đoán.

---

## 1. DANH MỤC GIẢ ĐỊNH KỸ THUẬT (ARCHITECTURAL ASSUMPTIONS)

Các giả định dưới đây đã được kiểm chứng thực nghiệm trực tiếp trên dữ liệu nguồn `C:\Data\kcht_json_2026-10-05`:

1. **Giả định 1: Hệ quy chiếu không gian là WGS 84 (EPSG:4326):**
   - *Cơ sở kiểm chứng:* Tệp `field_dictionary.json` định nghĩa các trường tọa độ có alias rõ ràng là `"Tọa độ X (WGS84)"` và `"Tọa độ Y (WGS84)"`. Giá trị mẫu đều nằm trong dải kinh vĩ độ Việt Nam ($X \approx 106.7^\circ, Y \approx 21.9^\circ$).
2. **Giả định 2: Tính duy nhất của khóa tự nhiên `(dataset_code, record_key)`:**
   - *Cơ sở kiểm chứng:* Đã kiểm tra đối soát trên 194,549 bản ghi mẫu, không phát hiện va chạm khóa chéo giữa các loại tài sản. Đối với 8 dataset có bản ghi trùng lặp nội bộ trong cùng 1 file, hệ thống áp dụng cơ chế deduplication lấy bản ghi mới nhất.
3. **Giả định 3: Quy tắc bóc tách phân cấp cha - con (`parent_id`):**
   - *Cơ sở kiểm chứng:* Giá trị `parent_id` trong các bảng công trình con (như mố trụ `tbl_pierstr`, đường gom `duonggom`) có dạng `<parent_id>_<tên_bảng_con>`. Cắt bỏ phần hậu tố sẽ trỏ chính xác về mã định danh của công trình cha.
4. **Giả định 4: Bản chất của 296 tập dữ liệu `maintenance_*`:**
   - *Cơ sở kiểm chứng:* Cấu trúc các tệp `maintenance_detail_*_chitiet.json` chỉ chứa 6 trường trích xuất từ bảng tài sản gốc. Do đó, đây là dữ liệu báo cáo động chứ không phải bảng giao dịch độc lập.
5. **Giả định 5: Hệ thống tài khoản người dùng:**
   - *Cơ sở kiểm chứng:* `manifest.json` xác nhận bộ dữ liệu không chứa mật khẩu hoặc token. Cần khởi tạo schema phân quyền RBAC mới hoàn toàn.

---

## 2. CÁC CÂU HỎI MỞ CẦN CHỦ ĐẦU TƯ / NGƯỜI DÙNG PHÊ DUYỆT (OPEN QUESTIONS)

### Câu hỏi 1: Lựa chọn Thư viện Giao diện Duy nhất cho Frontend (Single UI Library) - [ĐÃ CHỐT VÀ HOÀN TẤT TẠI GIAI ĐOẠN 6, 7, 8]
- **Bối cảnh:** Kiến trúc yêu cầu sử dụng **một UI library duy nhất** cho ứng dụng React + TypeScript + Vite.
- **Phương án đã chốt và triển khai:** **Phương án 1A: Ant Design 5 (vi-VN locale).**
  - Đã tích hợp trọn vẹn toàn bộ hệ sinh thái: Tree (lazy-load), Table (dynamic columns, row selection), Card, Descriptions, Drawer, Tabs, Breadcrumb, Tag, Alert, Notification/App Context.
  - Tối ưu hiệu năng, hiển thị chuẩn hành chính tương đồng với cổng `kcht.drvn.gov.vn`.

---

### Câu hỏi 8: Giới hạn Xuất Dữ liệu Báo cáo CSV Trực tuyến (Real-time vs Batch Export)
- **Bối cảnh:** Tại Giai đoạn 8, tính năng xuất CSV trực tuyến được giới hạn tối đa 2.000 bản ghi/lần nhằm bảo vệ bộ nhớ heap của JVM và không gây nghẽn kết nối cơ sở dữ liệu khi nhiều người dùng cùng tải dữ liệu biển báo (`tbl_road_sign` có 222.112 dòng).
- **Các phương án:**
  - **Phương án 8A (Hiện tại):** Xuất streaming trực tiếp từ PostGIS với limit 2.000 dòng có UTF-8 BOM, đáp ứng 95% nhu cầu trích xuất danh sách theo bộ lọc của cán bộ quản lý đường bộ.
  - **Phương án 8B (Nâng cao):** Bổ sung chức năng "Xuất dữ liệu phi tập trung (Async Job)" cho phép xuất toàn bộ 222k dòng nén tệp zip đưa vào MinIO và thông báo khi hoàn tất.
- **Ý kiến đề xuất:** Người dùng phê duyệt triển khai Phương án 8A trước, sau đó nếu có yêu cầu xuất toàn bộ kho dữ liệu lớn thì bổ sung Phương án 8B ở Giai đoạn 10 (Báo cáo & Tài liệu).

---

### Câu hỏi 2: Lựa chọn Mô hình Bảng Curated cho Tài sản
- **Bối cảnh:** Có 57 loại tài sản vật lý với hơn 833,000 bản ghi.
- **Các phương án:**
  - **Phương án 2A (Khuyến nghị): Hợp nhất hoàn toàn vào bảng `asset_record` (kèm `asset_geometry`).**
    - Toàn bộ 57 tài sản lưu chung; các trường tìm kiếm/lý trình được typed; thuộc tính riêng lưu trong cột `attributes` JSONB.
    - *Ưu điểm:* API tìm kiếm toàn cục cực nhanh; bản đồ GIS chỉ cần đọc 1 layer duy nhất; code Spring Boot tinh gọn.
  - **Phương án 2B: Hợp nhất phần lớn nhưng tách riêng 5 bảng cứng cho 5 loại tài sản lớn nhất:**
    - `curated_road_sign` (Biển báo - 222,112 dòng)
    - `curated_sphere_mirror` (Gương cầu/Cột cần vươn - 191,928 dòng)
    - `curated_drainage` (Cống rãnh dọc & ngang - 121,877 dòng)
    - `curated_guardrail` (Hộ lan tôn sóng - 51,697 dòng)
    - `curated_bridge` (Cầu đường bộ - 11,631 dòng kèm mố trụ)
- **Ý kiến đề xuất:** Người dùng phê duyệt **Phương án 2A (Hợp nhất hoàn toàn)** hay **Phương án 2B (Tách riêng top 5)**?

---

### Câu hỏi 3: Quy tắc Thể hiện Hình học Tuyến (Line Geometry Fidelity)
- **Bối cảnh:** Các tài sản dạng tuyến (`tbl_guardrail`, `duonggom`, `tbl_longitudinal`) trong dữ liệu nguồn chỉ có 2 tọa độ: điểm đầu (`from_coordinatex/y`) và điểm cuối (`to_coordinatex/y`).
- **Các phương án:**
  - **Phương án 3A (Khuyến nghị):** Lưu trữ đoạn thẳng 2 điểm `LINESTRING(from_pt, to_pt)` trong PostGIS ở giai đoạn 1. Sang giai đoạn sau sẽ khớp hình học (Map Snapping) theo tim đường quốc lộ thực tế.
  - **Phương án 3B:** Chỉ lưu điểm đầu `POINT(from_pt)` đại diện cho vị trí xuất phát của tài sản.
- **Ý kiến đề xuất:** Người dùng đồng ý với Phương án 3A hay có yêu cầu khác?

---

### Câu hỏi 4: Cơ chế Xác thực và Đăng nhập (Authentication Mechanism) - [ĐÃ GIẢI QUYẾT TẠI GIAI ĐOẠN 5]
- **Bối cảnh:** Hệ thống cần bảo vệ các endpoint quản trị và phân quyền khai thác.
- **Phương án đã chốt và triển khai:** **Phương án 4A:** Xác thực cục bộ (Local Database Authentication) sử dụng Spring Security 6, bảng `app_user` (BCrypt password hash cost 12), JWT Bearer Token ngắn hạn (15 phút), Refresh Token xoay vòng đơn kỳ lưu trong bảng `app_refresh_token` (mã băm SHA-256) và HttpOnly Cookie. Đã bổ sung bộ chặn Rate Limit (5 lần thử sai / 15 phút) và ghi nhận Audit Log khử bỏ 100% credential/secret.
- **Lộ trình tương lai:** Khi Trung tâm CNTT Cục Đường bộ cấp thông số SSO/OAuth2/OpenID Connect, hệ thống sẽ bổ sung OAuth2 Resource Server adapter mà không làm xáo trộn bảng `app_user` hiện có.

---

### Câu hỏi 5: Phương án Đồng bộ 309 Tệp Tài liệu Kỹ thuật Nhị phân
- **Bối cảnh:** Tệp `document_files_index.json` chứa thông tin 309 tệp nhị phân (PDF, DWG, DOCX) liên kết tới endpoint nguồn `/file-transfer/get-file`.
- **Các phương án:**
  - **Phương án 5A (Khuyến nghị):** Khởi tạo metadata trong PostgreSQL trước; cung cấp tiến trình worker đồng bộ tải các tệp nhị phân về lưu vào bucket `kcht-documents` của MinIO khi có kết nối mạng tới server nguồn.
  - **Phương án 5B:** Chỉ lưu metadata thông tin hồ sơ; khi người dùng bấm tải tài liệu trên website thì hệ thống mới proxy tải trực tiếp từ server nguồn.
- **Ý kiến đề xuất:** Người dùng muốn đồng bộ toàn bộ về MinIO cục bộ (Phương án 5A) hay tải theo nhu cầu (Phương án 5B)?

---

### Câu hỏi 6: Hình thức Triển khai Worker Nạp Dữ liệu (Import Worker Runtime)
- **Bối cảnh:** Dữ liệu nguồn gồm 658 tệp (~3.86 GB), việc nạp mất khoảng 5-10 phút.
- **Các phương án:**
  - **Phương án 6A (Khuyến nghị):** Worker tích hợp sẵn trong ứng dụng dưới dạng CLI Runner hoặc Trigger API (`/api/import/start-job`), sử dụng luồng nền (Background Thread) kèm checkpointing.
  - **Phương án 6B:** Đóng gói thành một Container độc lập riêng (`kcht-import-worker`) chỉ chạy khi có lệnh nạp dữ liệu.
- **Ý kiến đề xuất:** Người dùng muốn tiếp tục dùng phương án CLI/API nội bộ (Phương án 6A) hay tách riêng container worker (Phương án 6B)?

---

### Câu hỏi 7: Thời điểm Kích hoạt Tiến trình Chuyển đổi ETL từ Raw Ingestion sang Curated
- **Bối cảnh:** Toàn bộ 1,102,397 bản ghi thô đã được nạp an toàn vào `raw_dataset_record`. Tầng Curated (`asset_record`, `asset_geometry`, `reference_catalog`, `document_folder`, `document_metadata`) đã hoàn thiện schema, entity JPA, repository và query adapter.
- **Các phương án:**
  - **Phương án 7A (Khuyến nghị):** Ở Giai đoạn 3 (Smoke test & Tích hợp), tuân thủ đúng yêu cầu chỉ chạy read-only đối với dữ liệu đã nạp thông qua `AssetQueryAdapter` (tận dụng cơ chế Fallback Bridge) mà không chạy lại import hoặc thay đổi dữ liệu; tiến trình ETL chuyển đổi Curated toàn diện sẽ được thực hiện có kiểm soát sau khi hoàn thiện kiểm tra.
  - **Phương án 7B:** Viết script SQL/Java chuyển đổi ngay lập tức cho 3 dataset smoke test (`mst_national_road`, `tbl_bridge`, `tbl_road_sign`) sang `asset_record`.
- **Ý kiến đề xuất:** Áp dụng Phương án 7A nhằm bảo đảm tính độc lập từng giai đoạn và không can thiệp sớm vào dữ liệu khi chưa qua giai đoạn tích hợp read-only.

---

### Câu hỏi 9: Quy trình Kiểm duyệt & Số hóa Biên tập Hình học GIS (Spatial Geometry Digitizing Rules)
- **Bối cảnh:** Tại Giai đoạn 11 (Quản trị & CRUD), hệ thống bảo vệ nghiêm ngặt các trường hệ thống (`id`, `raw_record_id`, `version`, `created_at`) và trường hình học tọa độ không gian (`geom`), không cho phép sửa đổi tự do qua form CRUD thông thường nhằm tránh sai lệch trắc địa hoặc tọa độ chuẩn.
- **Các phương án:**
  - **Phương án 9A (Khuyến nghị):** Chỉ cho phép chỉnh sửa tọa độ/hình học thông qua công cụ bản đồ GIS tương tác chuyên dụng (WebGIS Digitizer Tool) có ràng buộc Snap vào tim đường quốc lộ, giới hạn sai số trắc địa và yêu cầu vai trò `ROLE_MANAGER` hoặc `ROLE_ADMIN` phê duyệt phiếu thay đổi tọa độ.
  - **Phương án 9B:** Cho phép nhập trực tiếp kinh độ / vĩ độ (X, Y) hoặc WKT (Well-Known Text) ngay trên form CRUD của từng tài sản khi người dùng có vai trò `ROLE_ADMIN`.
- **Ý kiến đề xuất:** Triển khai theo Phương án 9A ở giai đoạn tích hợp GIS nâng cao tiếp theo để bảo đảm chất lượng dữ liệu không gian của Cục Đường bộ Việt Nam.

---

### Câu hỏi 10: Phương án Mở rộng Tìm kiếm Toàn văn và Bản đồ Vector Tile khi Scale Hệ thống (Đúc kết từ Benchmark Giai đoạn 13)
- **Bối cảnh:** Kết quả benchmark thực nghiệm Giai đoạn 13 trên tập dữ liệu thực tế 1.102.397 bản ghi (~2.3 GB CSDL) cho thấy:
  1. Kỹ thuật *Deferred Join* và *B-Tree Composite Index* đã đưa độ trễ phân trang danh sách xuống mức siêu tốc **0,137 ms (DB) / 8 ms (HTTP)**.
  2. Kỹ thuật *Dynamic Grid Clustering (`ST_SnapToGrid`)* trên PostGIS đã nén dữ liệu mạng **99,86%** (từ 55.612 điểm xuống 157 cụm, chỉ 28 KB payload), bảo vệ trình duyệt an toàn.
  3. Tuy nhiên, với tìm kiếm văn bản phức tạp qua nhiều trường JSONB TOAST, độ trễ hiện tại đạt p50: **3.008 ms**, p95: **3.349 ms**; và tính toán phân cụm toàn quốc cho 222k biển báo mất ~18 giây nếu không có cache kết quả.
- **Các phương án:**
  - **Phương án 10A (Khuyến nghị cho hiện tại):** Duy trì kiến trúc Modular Monolith chuẩn hóa; sử dụng PostgreSQL PostGIS Engine kết hợp bộ nhớ đệm ứng dụng Caffeine Cache / Redis để cache kết quả phân cụm BBOX và danh mục. Không bổ sung thêm hạ tầng máy chủ phức tạp khi tải người dùng đồng thời hiện tại chưa vượt ngưỡng.
  - **Phương án 10B (Mở rộng trong tương lai khi CCU > 100 người dùng GIS cùng lúc):** Bổ sung một dịch vụ chuyên dụng phục vụ bản đồ vector tile (`pg_tileserv` hoặc `Martin`) để sinh Mapbox Vector Tiles (.pbf) trực tiếp từ PostGIS và đồng bộ sang Elasticsearch / OpenSearch phục vụ tìm kiếm toàn văn siêu tốc (< 50 ms).
- **Ý kiến đề xuất:** Giữ nguyên Phương án 10A theo đúng nguyên tắc cam kết *"Chỉ thêm cache, index, cursor pagination hoặc vector tile khi benchmark cho thấy cần thiết. Không giảm dữ liệu hiển thị mà không thông báo."* Khi triển khai thực tế trên diện rộng (Giai đoạn vận hành mở rộng), nếu có yêu cầu tăng đột biến thì mới kích hoạt Phương án 10B.

