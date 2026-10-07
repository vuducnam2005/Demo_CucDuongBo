# 0001. QUYẾT ĐỊNH KIẾN TRÚC KHỞI TẠO VÀ CÁC ĐÁNH ĐỔI CÔNG NGHỆ (INITIAL ARCHITECTURE)

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)  
- **Ngày quyết định:** 2026-10-05  
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT  
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam  

---

## 1. BỐI CẢNH VÀ ĐẶT VẤN ĐỀ (CONTEXT & PROBLEM STATEMENT)

Cục Đường bộ Việt Nam cần xây dựng và hiện đại hóa website quản lý kết cấu hạ tầng giao thông đường bộ (tương tự cổng điều hành mẫu `https://kcht.drvn.gov.vn/dashboard`). Hệ thống phục vụ việc quản lý, giám sát, tra cứu bản đồ số WebGIS và phân tích dữ liệu trên phạm vi toàn quốc.

Dữ liệu nguồn thu thập được từ hệ thống cũ (snapshot ngày 05/10/2026) bao gồm:
- **658 tệp JSON** (kèm 658 tệp metadata) với tổng dung lượng khoảng **3.86 GB**.
- **1,104,088 bản ghi** (trong đó có hơn 833,000 công trình tài sản thực thể, 152 danh mục tham chiếu, 123 tệp quản lý hồ sơ và 314 tập báo cáo bảo trì định kỳ).
- **309 tệp tài liệu kỹ thuật nhị phân** (PDF, Excel, Word, CAD/DWG, ảnh hiện trường).
- Yêu cầu khả năng hiển thị không gian WebGIS mượt mà, tìm kiếm nhanh theo tuyến, lý trình, địa bàn hành chính, hỗ trợ quản trị nạp dữ liệu định kỳ và bảo mật nghiêm ngặt.

Hệ thống cần các quyết định kiến trúc nền tảng để vừa đảm bảo tốc độ triển khai ban đầu, vừa chịu tải tốt, không làm mất dữ liệu, và có lộ trình mở rộng dài hạn.

---

## 2. CÁC QUYẾT ĐỊNH CÔNG NGHỆ VÀ LỰA CHỌN THIẾT KẾ (DECISIONS)

### Quyết định 1: Lựa chọn Mô hình Modular Monolith kết hợp Import Worker Độc lập
- **Quyết định:** Triển khai hệ thống ban đầu dưới dạng **Modular Monolith** trong Spring Boot 3, chia tách các module logic theo ranh giới nghiệp vụ:
  - `Asset module`: Quản lý nghiệp vụ công trình, lý trình, phân cấp đường bộ.
  - `GIS/Map module`: Quản lý truy vấn không gian, BBOX, GeoJSON RFC 7946, vector tiles.
  - `Report module`: Báo cáo chiều dài đường, bảo trì tổng hợp/chi tiết động.
  - `Document module`: Quản lý hồ sơ hoàn công, metadata tài liệu, adapter MinIO/S3.
  - `Reference module`: 152 danh mục tham chiếu, từ điển metadata.
  - `User/Permission module`: Quản lý tài khoản, RBAC (viewer, editor, manager, admin), audit log.
  - `Import module`: Điều phối job nạp, tiến độ, checkpoint, resume, đối soát số dòng.
- **Tác vụ nạp dữ liệu lớn:** Được thiết kế như một **Worker Độc lập** chạy ngoài request web, dùng Jackson Streaming $O(1)$ RAM, batch 500 bản ghi, checkpointing và đối soát số dòng.
- **Lý do lựa chọn:**
  - Microservices ngay từ đầu sẽ gây bùng nổ độ phức tạp (network latency, distributed transaction, CI/CD phức tạp) trong khi cần tập trung chuẩn hóa 1.1 triệu bản ghi.
  - Modular Monolith cho phép phát triển nhanh, refactor an toàn, chia sẻ transactional boundary trong database khi cần thiết, nhưng vẫn thiết lập ranh giới tách service rõ ràng sau này.

---

### Quyết định 2: Mô hình Cơ sở Dữ liệu Hai lớp (Two-Tier Hybrid Architecture)
- **Quyết định:** Sử dụng kết hợp **PostgreSQL 16** với tiện ích mở rộng **PostGIS 3.4+**:
  1. *Lớp Raw Ingestion (`raw_dataset_record`):* Lưu trữ nguyên bản JSONB, băm SHA-256 payload, bảo lưu 100% dữ liệu gốc không chỉnh sửa.
  2. *Lớp Curated ODS (`asset_record` & `asset_geometry`):* Hợp nhất 57 tập tài sản vào một bảng thống nhất. Các trường dùng để tìm kiếm, phân quyền và lý trình được định kiểu (Typed Columns: `route_code`, `km_from`, `province_id`, `state`), các thuộc tính kỹ thuật chi tiết của từng loại công trình lưu trong cột JSONB `attributes`. Tọa độ lưu riêng trong bảng `asset_geometry`.
- **Lý do bác bỏ Anti-pattern 658 Bảng cứng:**
  - Nếu tạo 658 bảng riêng biệt trong database, PostgreSQL sẽ bị quá tải metadata danh mục (`pg_class`), không thể thực hiện tìm kiếm toàn cục (Global Search) trên toàn bộ hạ tầng đường bộ, và cực kỳ khó khăn khi bảo trì phân quyền.
- **Lý do bác bỏ Pure NoSQL (MongoDB):**
  - Dữ liệu hạ tầng đường bộ đòi hỏi quan hệ toàn vẹn chặt chẽ (cha - con giữa cầu và mố trụ, liên kết hồ sơ tài liệu), đồng thời yêu cầu các phép toán không gian địa lý phức tạp (giao cắt, khoảng cách theo mét, buffer) mà PostGIS vượt trội hoàn toàn so với MongoDB Geospatial.

---

### Quyết định 3: Backend Stack với Java 21 & Spring Boot 3.3
- **Quyết định:** Sử dụng Java 21 LTS cùng hệ sinh thái Spring Boot 3.3+, Spring Security, Spring Data JPA / Hibernate 6, Flyway và OpenAPI 3 (SpringDoc).
- **Lý do lựa chọn:**
  - **Java 21 Virtual Threads (Loom):** Tối ưu hóa throughput cho các tác vụ I/O nặng (REST API, đọc file JSON lớn, truy vấn spatial song song).
  - **Hibernate 6:** Hỗ trợ native cho PostgreSQL JSONB (`@JdbcTypeCode(SqlTypes.JSON)`) và PostGIS spatial geometry.
  - **Flyway:** Đảm bảo toàn bộ schema DDL được quản lý phiên bản nghiêm ngặt, có lịch sử kiểm toán và dễ dàng kiểm soát qua Docker Compose.

---

### Quyết định 4: Frontend Stack với React + TypeScript + Vite + TanStack Query + Single UI Library
- **Quyết định:** Xây dựng Single Page Application (SPA) bằng React 18+, TypeScript 5+, Vite, React Router v6, TanStack Query (React Query) và **duy nhất một UI Library chuẩn** (Ant Design hoặc MUI).
- **Lựa chọn UI Library:**
  - Khuyến nghị: **Ant Design 5 (vi-VN locale)** vì cung cấp sẵn Table phân trang mạnh mẽ, Tree phân cấp, Form validation và phong cách tương thích cao với website mẫu `kcht.drvn.gov.vn`.
- **Lựa chọn Bản đồ WebGIS:**
  - Chọn **OpenLayers** (thay vì Leaflet cơ bản) do tính năng hỗ trợ chuyên sâu các lớp GIS chuyên đề giao thông, BBOX spatial query, vector tiles và geometry phức tạp.
- **Lý do chọn TanStack Query:**
  - Tự động quản lý caching server state, pagination, refetching và đồng bộ dữ liệu bản đồ mà không cần viết boilerplate Redux phức tạp.

---

### Quyết định 5: Lưu trữ Tệp tin Độc lập bằng MinIO / S3 Storage
- **Quyết định:** Sử dụng **MinIO** (tương thích AWS S3 API) qua Docker Compose cho môi trường phát triển cục bộ và staging; môi trường production có thể dùng MinIO Cluster hoặc AWS S3 / Cloud Storage.
- **Lý do lựa chọn:**
  - Cơ sở dữ liệu PostgreSQL chỉ lưu metadata và mã băm SHA-256 của 309 tệp tài liệu kỹ thuật nhị phân.
  - Lưu tệp nhị phân trên Object Storage giúp cơ sở dữ liệu gọn nhẹ, sao lưu database nhanh chóng, hỗ trợ phục vụ tải file qua Pre-signed URL bảo mật mà không tốn RAM của backend server.

---

### Quyết định 6: Chiến lược Kiểm thử Tự động (Testing Strategy)
- **Quyết định:**
  - **Unit Testing:** JUnit 5 + Mockito cho logic service, parsing, validator.
  - **Integration Testing:** **Testcontainers** kết hợp PostgreSQL 16 + PostGIS 3.4+ image thật để kiểm thử câu lệnh JPA/Spatial SQL chính xác.
  - **End-to-End Testing:** **Playwright** kiểm thử toàn diện giao diện người dùng (login, dashboard, tree, table, map popup, report download).

---

### Quyết định 7: Kiến trúc Triển khai Production với Nginx Reverse Proxy
- **Quyết định:** Sử dụng **Nginx** làm reverse proxy đứng trước Spring Boot và React static build.
- **Lý do lựa chọn:**
  - Đảm nhiệm TLS Termination (HTTPS), nén Gzip/Brotli, rate limiting tầng mạng và định tuyến `/api/**` về backend Spring Boot, các route khác về SPA React frontend.

---

## 3. CÁC ĐÁNH ĐỔI KỸ THUẬT (TRADE-OFFS & CONSEQUENCES)

| Lựa chọn kỹ thuật | Điểm mạnh (Pros) | Đánh đổi / Hạn chế (Cons) | Giải pháp giảm thiểu rủi ro (Mitigation) |
| :--- | :--- | :--- | :--- |
| **Cột thuộc tính mở rộng JSONB trong `asset_record`** | Linh hoạt tối đa cho hàng trăm loại công trình khác nhau; không phải tạo 57 bảng; thêm thuộc tính mới không cần đổi DDL. | Tốc độ lọc trên trường JSONB chậm hơn cột Typed nếu không có chỉ mục phù hợp. | Tạo chỉ mục `GIN (attributes jsonb_path_ops)`. Nếu trường nào được lọc với tần suất cao (ví dụ: `capduong`, `duanbot`), sẽ migrate thành cột Typed riêng. |
| **Tách bảng `asset_geometry` riêng biệt** | Bảng `asset_record` cực kỳ gọn nhẹ cho các truy vấn bảng biểu thông thường; tối ưu chỉ mục GiST không gian. | Phải thực hiện phép JOIN giữa `asset_record` và `asset_geometry` khi cần cả dữ liệu thuộc tính lẫn hình học. | Chỉ JOIN khi client gọi endpoint không gian `/geo` hoặc tìm kiếm theo bán kính/hộp bao. |
| **Modular Monolith ban đầu** | Tốc độ phát triển cực nhanh; quản lý mã nguồn đơn giản; không có độ trễ mạng liên dịch vụ. | Nếu một module gặp sự cố ngốn CPU (ví dụ: tác vụ tính toán báo cáo nặng), có thể ảnh hưởng đến toàn bộ ứng dụng. | Cô lập tác vụ nạp dữ liệu vào Worker độc lập; áp dụng Virtual Threads và đặt timeout chặt chẽ cho các truy vấn SQL. |
| **Sử dụng MinIO Object Storage** | Chuẩn hóa theo kiến trúc Cloud-native; dễ dàng mở rộng dung lượng tệp tài liệu lên hàng Terabyte. | Bổ sung thêm một dịch vụ hạ tầng phụ thuộc trong Docker Compose. | Đã cấu hình tự động trong `docker-compose.yml` với healthcheck và volume bền vững. |
| **OpenLayers cho WebGIS** | Mạnh mẽ, hỗ trợ nhiều hệ quy chiếu, vector tiles, vẽ và đo đạc hình học chuyên sâu. | Kích thước thư viện lớn hơn Leaflet, đường cong học dốc hơn. | Đóng gói thành WebGIS Component module hóa, tree-shaking khi build Vite. |

---

## 4. 6 QUYẾT ĐỊNH NỀN TẢNG BẮT BUỘC CHỐT NGAY

1. Dùng PostgreSQL làm nguồn dữ liệu vận hành; không dùng hàng trăm file JSON làm database production.
2. Giữ JSON làm nguồn import, backup và đối soát.
3. Dùng `dataset` (`dataset_key`) và `asset_id` (`record_key`) làm định danh dữ liệu; mỗi dataset có schema và mapping riêng.
4. Không sao chép cookie, token, mật khẩu, session hoặc cơ chế bảo mật của website mẫu.
5. Không sao chép logo, hình ảnh, CSS hoặc nội dung có bản quyền nếu chưa có quyền sử dụng.
6. Kiến trúc mặc định là modular monolith; import là worker/process độc lập. Không tạo microservice đầy đủ trước khi có bằng chứng về tải, đội ngũ hoặc yêu cầu triển khai độc lập.
