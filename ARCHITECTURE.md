# TÀI LIỆU THIẾT KẾ KIẾN TRÚC TOÀN DIỆN (ARCHITECTURE)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  

---

## 1. TỔNG QUAN KIẾN TRÚC HỆ THỐNG ĐÃ ĐƯỢC CHỐT

Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ được thống nhất khởi đầu bằng mô hình **Modular Monolith + Import Worker**, chưa triển khai microservices đầy đủ nhằm tối ưu tốc độ phát triển, kiểm soát toàn vẹn giao dịch và bảo toàn dữ liệu trên hơn 1.1 triệu bản ghi tài sản, 152 danh mục tham chiếu, 309 tài liệu kỹ thuật nhị phân và dữ liệu không gian PostGIS phục vụ WebGIS.

```text
React / TypeScript / Vite (Single UI Library: Ant Design / MUI) + OpenLayers WebGIS
                                        |
                               REST API / GeoJSON / HTTPS
                                        v
Spring Boot modular monolith (Java 21, Spring Data JPA, Spring Security, Flyway, OpenAPI)
  |-- Asset module (quản lý nghiệp vụ công trình, lý trình, tìm kiếm thuộc tính)
  |-- GIS/Map module (truy vấn spatial, BBOX, GeoJSON RFC 7946, vector tiles)
  |-- Report module (báo cáo chiều dài đường, bảo trì tổng hợp/chi tiết động)
  |-- Document module (hồ sơ hoàn công, file nhị phân, MinIO/S3 adapter)
  |-- Reference module (152 danh mục tham chiếu, từ điển metadata)
  |-- User/Permission module (RBAC: viewer, editor, manager, admin; audit log)
  `-- Import module (điều phối job, tiến độ, checkpoint, resume, đối soát số dòng)
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
PostgreSQL 16 + PostGIS 3.4+                              MinIO / S3 Object Storage
- Raw Lake: raw_dataset_record (JSONB bất biến)          - Bucket `kcht-documents`: PDF, CAD, Word, Excel
- Curated ODS: asset_record, asset_geometry             - Bucket `kcht-media`: Ảnh hiện trường
- Reference Catalogs, Document Meta, RBAC, Audit        - Bucket `kcht-quarantine`: Tệp nghi ngờ
- Flyway Versioned Migrations

Import worker (process / job độc lập với request web)
  `-- đọc JSON theo batch (Jackson Streaming), checkpoint, resume, retry và lưu file/dòng lỗi
```

---

## 2. NỀN TẢNG CÔNG NGHỆ BỔ SUNG ĐÃ ĐƯỢC CHỐT

- **Backend:** Java 21, Spring Boot 3.3+, Spring Web, Spring Data JPA, Spring Security.
- **Database & Spatial:** PostgreSQL 16 + **PostGIS 3.4+** (lưu và truy vấn `Point`, `LineString`, `Polygon`, SRID 4326).
- **Schema Migration:** **Flyway 10** (quản lý phiên bản hóa toàn bộ migration database).
- **API Spec & Testing:** **OpenAPI 3.0 / Swagger UI** (mô tả, tài liệu hóa và kiểm thử API tương tác).
- **Frontend:** React 18+, TypeScript 5+, Vite.
- **Routing & Client State:** **React Router v6** quản lý route; **TanStack Query** quản lý cache, retry, loading state và phân trang API.
- **Single UI Library:** Chọn một thư viện duy nhất (**Ant Design** hoặc **MUI**) cho bảng, form, modal, tree và layout. Khuyến nghị Ant Design 5 (vi-VN) cho dashboard hành chính.
- **WebGIS:** **OpenLayers** (hoặc Leaflet). Chọn OpenLayers vì yêu cầu bản đồ giao thông cần quản lý nhiều lớp GIS chuyên đề, vector tiles, bounding-box và geometry phức tạp.
- **File Storage:** **MinIO / S3** lưu trữ PDF, Word, Excel, CAD/DWG và hình ảnh ngoài database. Database chỉ lưu metadata và object key.
- **Local Development:** **Docker Compose** chạy local PostgreSQL/PostGIS, backend, frontend, MinIO và Flyway.
- **End-to-End Testing:** **Playwright** kiểm thử tự động toàn diện các luồng người dùng (đăng nhập, dashboard, cây tài sản, tìm kiếm, bản đồ, xuất báo cáo).
- **Integration Testing:** **Testcontainers** kiểm thử Spring Boot với PostgreSQL/PostGIS container thật.
- **Production Reverse Proxy:** **Nginx** làm reverse proxy, TLS termination, nén gzip và định tuyến static assets.

---

## 3. 7 NGUYÊN TẮC CỐT LÕI ĐỂ CÓ THỂ TÁCH SERVICE SAU NÀY MÀ KHÔNG PHẢI VIẾT LẠI TOÀN BỘ

1. **Ranh giới Module Tự quản:** Mỗi module có package, service, repository, migration và API contract riêng. Module khác tuyệt đối không truy cập trực tiếp entity/repository của module đó.
2. **Giao tiếp Lỏng lẻo (Loose Coupling):** Giao tiếp giữa các module chỉ thực hiện qua Application Service / Port Interface hoặc Spring Application Events nội bộ; Shared Kernel chỉ chứa kiểu dữ liệu thật sự dùng chung (như Value Objects, PagedResponse, ErrorResponse).
3. **Worker Độc lập với Web Request:** Import worker chạy độc lập với request web, có thể dừng/chạy lại mà không làm sập API. Worker dùng chung contract và migration với backend nhưng không ghi secret vào log.
4. **Hệ Thống Định Danh Ổn định:** Sử dụng các định danh đã chốt trong database (`dataset_key` hoặc `dataset_id`, `record_key` hoặc `asset_id`, `import_job_id`) ổn định; phải có bảng ánh xạ rõ ràng và mọi job phải idempotent để sau này chuyển sang message queue hoặc microservice riêng vẫn đối soát được.
5. **Cách ly Transaction:** Tách transaction theo từng module nghiệp vụ; không sử dụng transaction xuyên module nếu không thật sự cần thiết.
6. **Phiên bản hóa Hợp đồng (Versioned Contracts):** API, schema dữ liệu, quyền truy cập và event phải được version hóa; frontend không phụ thuộc trực tiếp vào bảng SQL hay cấu trúc nội bộ database.
7. **Trừu tượng hóa Hạ tầng (Infrastructure Abstraction):** Có interface cho message queue, object storage, cache nhưng mặc định sử dụng implementation đơn giản ở local. Chỉ thay bằng Kafka/RabbitMQ, Redis cluster hoặc service riêng khi benchmark và vận hành thực tế chứng minh cần thiết.

---

## 4. CÁC ĐƯỜNG LUI VÀ ĐIỀU KIỆN CHUYỂN ĐỔI KIẾN TRÚC (CONTINGENCY & TRANSITION PLAYBOOKS)

Hệ thống thiết lập 14 đường lui và điều kiện chuyển kiến trúc cụ thể. Các mục này là đường lui có điều kiện, không phải cam kết bắt buộc phải tách microservice:

1. **Tải thấp hoặc đội ngũ nhỏ:** Giữ các module trong một ứng dụng Spring Boot và một cụm PostgreSQL cluster duy nhất để tối ưu chi phí vận hành.
2. **Import dài hoặc chạy theo lịch:** Scale tiến trình `import-worker` độc lập theo số lượng job; chưa cần tách các module nghiệp vụ khác.
3. **GIS nặng:** Tách read/query GIS hoặc triển khai tile service riêng (Martin / pg_tileserv) sau khi benchmark bounding-box / vector tile cho thấy cần thiết.
4. **Báo cáo nặng:** Chuyển tác vụ xuất report sang job bất đồng bộ, lưu kết quả vào MinIO và cung cấp link tải xuống; chỉ tách Report Service khi cần scale tài nguyên CPU/RAM riêng.
5. **Nhiều đội phát triển hoặc cần triển khai độc lập:** Tách từng module theo ranh giới đã định, giữ API/event contract và migration hoàn toàn tương thích.
6. **Import lỗi:** Dừng job, resume từ checkpoint hoặc rollback batch lỗi; dữ liệu đã import thành công của các batch trước được giữ nguyên toàn vẹn.
7. **Schema thay đổi:** Sử dụng Flyway migration có version, tương thích ngược trong thời gian chuyển tiếp (Expand - Contract), có bản backup và kế hoạch rollback.
8. **Database hoặc storage tạm thời không khả dụng:** Thực hiện retry có exponential backoff, giới hạn thời gian (timeout), circuit breaker ở boundary phù hợp, ghi trạng thái job rõ ràng và resume sau khi dịch vụ hồi phục; tuyệt đối không đánh dấu thành công giả.
9. **Queue được thêm về sau:** Sử dụng mẫu thiết kế Outbox Pattern và Idempotent Consumer, có cơ chế retry và Dead-Letter Queue (DLQ); vẫn duy trì đường chạy không queue (in-memory / direct call) cho môi trường local và máy chủ nhỏ.
10. **Dữ liệu sai hoặc import nhầm:** Giữ raw payload bất biến (`raw_dataset_record`), lưu mã băm source hash và import job, tạo báo cáo đối soát, cho phép reprocess có kiểm soát và phục hồi từ bản sao lưu trước khi sửa đổi dữ liệu curated.
11. **Deploy hoặc migration lỗi:** Sử dụng bản backup trước release, áp dụng expand/contract migration, kiểm tra health check `/actuator/health`, rollback ứng dụng và thực hiện quy trình restore đã kiểm thử định kỳ.
12. **Thông tin xác thực hoặc khóa ký phải thay đổi (Credential Rotation):** Hỗ trợ cơ chế xoay vòng secret/key (Dual-Key verification), thu hồi session/token khi cần và không yêu cầu sửa đổi mã nguồn (cấu hình hoàn toàn qua environment variables / secret manager).
13. **API quá tải:** Áp dụng giới hạn thời gian truy vấn (`statement_timeout = 5s`), phân trang bắt buộc `size <= 100`, rate limit, bộ đệm cache (Caffeine/Redis) hoặc PostgreSQL Read Replica theo benchmark; thông báo lỗi có thể thử lại và chuyển báo cáo nặng sang job bất đồng bộ.
14. **File tài liệu lỗi hoặc mất object:** Kiểm tra checksum SHA-256, metadata trạng thái, retry tải lên/tải xuống, phát hiện và dọn dẹp object mồ côi, và phục hồi từ bản sao lưu MinIO/S3.

---

## 5. 6 QUYẾT ĐỊNH BẮT BUỘC NÊN CHỐT NGAY

1. **Nguồn Dữ liệu Vận hành:** Dùng PostgreSQL làm nguồn dữ liệu vận hành chính thức; tuyệt đối không dùng hàng trăm file JSON làm database production.
2. **Vai trò của File JSON:** Giữ bộ dữ liệu JSON trong `C:\Data\kcht_json_2026-10-05` làm nguồn import ban đầu, nguồn đối soát số dòng và cơ sở kiểm tra tính toàn vẹn.
3. **Định danh Dữ liệu Thống nhất:** Dùng `dataset` (`dataset_key`) và `asset_id` (`record_key`) làm định danh dữ liệu; mỗi dataset có schema và mapping riêng biệt.
4. **Không Sao chép Cơ chế Bảo mật Cũ:** Tuyệt đối không sao chép cookie, token, mật khẩu, session hoặc cơ chế bảo mật cũ của website mẫu `https://kcht.drvn.gov.vn/dashboard`. Triển khai mới hoàn toàn bằng Spring Security + JWT chuẩn RFC.
5. **Không Vi phạm Bản quyền:** Không sao chép logo, hình ảnh, CSS hoặc nội dung có bản quyền nếu chưa có quyền sử dụng hợp pháp từ Cục Đường bộ Việt Nam.
6. **Kiến trúc Mặc định:** Kiến trúc mặc định là **Modular Monolith**; tác vụ import là **Worker/Process Độc lập**. Không tạo microservices đầy đủ trước khi có bằng chứng thực tế về tải, ranh giới đội ngũ hoặc yêu cầu triển khai độc lập.
