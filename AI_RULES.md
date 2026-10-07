# QUY TẮC PHÁT TRIỂN VÀ VẬN HÀNH DÀNH CHO AI VÀ KỸ SƯ (AI_RULES)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  
**Áp dụng:** Toàn bộ AI Coding Agents và Kỹ sư tham gia phát triển dự án.

---

## 1. NGUYÊN TẮC CỐT LÕI (CORE PRINCIPLES)

1. **Tuyệt đối không làm mất dữ liệu (Zero Data Loss):** Dữ liệu hạ tầng đường bộ gồm hơn 1.1 triệu bản ghi và tài liệu pháp lý là tài sản quốc gia. Mọi thao tác ghi, cập nhật, di chuyển hoặc nạp dữ liệu phải có cơ chế sao lưu, kiểm tra tính toàn vẹn (checksum), chạy thử (dry-run) và khả năng hoàn tác (rollback).
2. **Tuân thủ ranh giới Modular Monolith:** Không viết mã nguồn gây phụ thuộc vòng (circular dependency) giữa các module. Mọi giao tiếp giữa các module nghiệp vụ phải qua Interface hoặc Application Events. Module này tuyệt đối không truy cập trực tiếp entity/repository của module khác.
3. **Thực thi theo bằng chứng dữ liệu thực tế (Evidence-Based):** Không tự suy đoán hoặc "bịa" cấu trúc dữ liệu. Mọi thay đổi về entity, mapping, tọa độ phải được kiểm chứng dựa trên tệp JSON nguồn tại `C:\Data\kcht_json_2026-10-05` và các tài liệu đặc tả hiện có. Nếu có điểm mâu thuẫn hoặc chưa chắc chắn, ghi ngay vào [`OPEN_QUESTIONS.md`](file:///c:/Demo_CucDuongBo/OPEN_QUESTIONS.md).
4. **Bảo mật mặc định & Độc lập Pháp lý:**
   - **Tuyệt đối không sao chép cookie, token, mật khẩu, session hoặc cơ chế bảo mật** của website mẫu `https://kcht.drvn.gov.vn/dashboard`. Triển khai mới hoàn toàn bằng Spring Security + JWT chuẩn RFC.
   - **Tuyệt đối không sao chép logo, hình ảnh, CSS hoặc nội dung có bản quyền** nếu chưa có văn bản cấp quyền sử dụng từ Cục Đường bộ Việt Nam.
5. **Không Tạo Thành phần Trùng lặp (No Duplicate Artifacts):** Nếu database, migration Flyway, bảng, seed dữ liệu hoặc import worker đã tồn tại trong repository, **tuyệt đối không tạo database thứ hai, migration thứ hai, bảng thứ hai hoặc importer thứ hai**.
6. **Code đi kèm Kiểm thử (Test-Driven & Verifiable):** Mỗi thay đổi logic nghiệp vụ, schema cơ sở dữ liệu hoặc API contract đều bắt buộc phải có automated test tương ứng (Unit Test, Integration Test với Testcontainers, E2E với Playwright). Không bàn giao task nếu test chưa chạy thành công 100%.

---

## 2. QUY TẮC CODING VÀ CÔNG NGHỆ CHUẨN

### 2.1 Backend (Java 21 & Spring Boot 3.3+)
- **Phiên bản & Tính năng Java 21:**
  - Tận dụng tối đa Java Record cho DTOs, Command objects, Query projections (bất biến - immutable).
  - Sử dụng Pattern Matching for `switch` và `instanceof` để phân loại dữ liệu rõ ràng.
  - Sử dụng Virtual Threads (Loom) cho các tác vụ I/O nặng (REST API call, đọc/ghi tệp, truy vấn database song song).
  - Không sử dụng Java Serialization mặc định; bắt buộc dùng Jackson JSON.
- **Spring Data JPA & Hibernate 6:**
  - Tuyệt đối không sử dụng `ddl-auto: create` hoặc `ddl-auto: update` trên bất kỳ môi trường nào ngoài bộ nhớ tạm test. Toàn bộ schema DDL phải do **Flyway** kiểm soát.
  - Phải bật `open-in-view: false` để tránh rò rỉ kết nối cơ sở dữ liệu (N+1 query trong view).
  - Với các quan hệ `@OneToMany`, `@ManyToMany`, bắt buộc sử dụng `FetchType.LAZY` và tránh eager fetching làm sập bộ nhớ.
  - Phải cấu hình batch processing rõ ràng: `hibernate.jdbc.batch_size: 500`, `order_inserts: true`, `order_updates: true`.
  - Kiểu dữ liệu JSONB trong entity phải dùng `@JdbcTypeCode(SqlTypes.JSON)`.
- **Ranh giới Module & Transaction:**
  - Tách biệt transaction theo từng module nghiệp vụ; không dùng distributed transaction xuyên module nếu không thật sự cần thiết.
- **Quản lý Luồng & Import Worker:**
  - Import worker chạy độc lập với request web, có thể dừng/chạy lại mà không làm sập API.
  - Worker dùng chung contract và migration với backend nhưng không ghi secret vào log.

### 2.2 Frontend (React + TypeScript + Vite)
- **Kiến trúc & Ngôn ngữ:**
  - Sử dụng TypeScript ở chế độ nghiêm ngặt (`strict: true`), cấm sử dụng kiểu `any` bừa bãi. Mọi dữ liệu API phải có interface/type tương ứng.
  - Quản lý trạng thái máy chủ (Server State) độc quyền bằng **TanStack Query** (React Query). Không lưu trữ dữ liệu cache server trong Redux/Zustand.
  - Quản lý trạng thái giao diện cục bộ (Client State) bằng lightweight store (Zustand hoặc React Context).
  - Duy trì **một UI Library duy nhất** (Ant Design hoặc MUI) trong toàn bộ dự án để đảm bảo tính nhất quán về design system, accessibility và kích thước bundle.
- **Bản đồ số & Không gian Địa lý (WebGIS):**
  - Sử dụng **OpenLayers** (hoặc Leaflet) cho các nhu cầu bản đồ phức tạp nhiều lớp chuyên đề.
  - Luôn hủy (destroy/cleanup) instance bản đồ khi component unmount để tránh rò rỉ bộ nhớ WebGL/Canvas.
  - Dữ liệu hiển thị bản đồ phải được đơn giản hóa hình học hoặc dùng Vector Tiles (MVT) / Bounding Box (BBOX) khi số lượng đối tượng vượt quá 2,000 điểm trên viewport. Tuyệt đối không tải đồng thời hàng trăm nghìn geometry vào browser.

### 2.3 Cơ sở Dữ liệu & PostGIS (PostgreSQL 16)
- **Hệ quy chiếu Không gian:** Toàn bộ dữ liệu tọa độ hình học bắt buộc quy chuẩn về **EPSG:4326 (WGS 84)** với thứ tự `(Longitude, Latitude)`.
- **Chỉ mục Bắt buộc:**
  - B-Tree index cho các trường lọc thường xuyên: `dataset_code`, `route_code`, `province_id`, `state`, `created_at`.
  - GiST index cho tất cả các cột không gian địa lý: `asset_geometry.geom`.
  - GIN index (`jsonb_path_ops`) cho các cột thuộc tính mở rộng JSONB: `asset_record.attributes`.
  - Trigram index (`gin_trgm_ops`) cho các trường hỗ trợ tìm kiếm mờ tiếng Việt không dấu/có dấu: `asset_record.name`, `route_code`.
- **An toàn Truy vấn SQL:**
  - Tuyệt đối cấm ghép chuỗi tạo raw SQL (tránh triệt để SQL Injection). Bắt buộc dùng JPA Parameters (`:param`), Spring Data Criteria API, hoặc parameterized JDBC.
  - Mọi câu lệnh truy vấn phân trang phải đi kèm `LIMIT` và `OFFSET`, khống chế `size <= 100`.
  - Tuyệt đối không cho phép client truyền tên bảng SQL tùy ý. Bắt buộc kiểm tra qua `dataset_registry` và allowlist.

---

## 3. NGUYÊN TẮC BẢO MẬT VÀ BẢO VỆ DỮ LIỆU (SECURITY & ZERO DATA LOSS)

### 3.1 Bảo vệ Thông tin Bí mật (Secrets & Credentials)
- **Cấm hard-code bí mật:** Tuyệt đối cấm commit mật khẩu, khóa bí mật JWT, S3 secret key, connection string cơ sở dữ liệu vào git repository hoặc file tài liệu markdown.
- **Sử dụng `.env`:** Toàn bộ thông số nhạy cảm được cấu hình thông qua biến môi trường hoặc tệp `.env` (đã nằm trong `.gitignore`). Cung cấp `.env.example` với giá trị giả định mẫu.
- **Ẩn thông tin nhạy cảm trong Logs (Log Masking):** Không in giá trị token, cookie, password, authorization header ra console hoặc file log. Sử dụng filter để mask các trường nhạy cảm trong JSON request body.

### 3.2 Quy định Bảo vệ Dữ liệu Nạp (Data Loss Prevention in Import)
1. **Nguyên tắc Bất biến Tầng Raw (Raw Layer Immutability):** Dữ liệu thu thập từ file JSON nạp vào `raw_dataset_record` là bất biến. Không được phép chỉnh sửa hoặc xóa dữ liệu thô này trong các luồng CRUD. Mọi chỉnh sửa dữ liệu người dùng phải đi qua bảng curated kèm trường audit và version.
2. **Kiểm tra Checksum:** Mọi file dữ liệu trước khi nạp phải được đối soát mã băm SHA-256 so với `manifest.json`. Nếu băm không khớp, file bị từ chối và ghi nhận cảnh báo.
3. **Cơ chế Nạp Lũy tiến (Idempotent Upsert):** Khi chạy nạp lại hoặc nạp tiếp (resume), hệ thống sử dụng cặp khóa `(dataset_code, record_key)` kết hợp `payload_hash`. Nếu bản ghi không thay đổi nội dung, bỏ qua (`SKIP`); nếu có cập nhật, ghi đè an toàn (`UPDATE`); nếu chưa có, thêm mới (`INSERT`). Tuyệt đối không tạo bản ghi trùng lặp (`0 Duplicate`).
4. **Giao dịch theo Batch (Transactional Batching):** Phân chia tiến trình thành các batch độc lập (500 - 1,000 bản ghi/batch). Mỗi batch được bọc trong một Database Transaction riêng biệt. Nếu một batch lỗi, chỉ rollback batch đó, lưu bản ghi lỗi vào `import_error` và tiếp tục xử lý batch tiếp theo mà không làm gián đoạn toàn bộ tiến trình.

### 3.3 Bảo vệ Tệp tin Tải lên (File Upload & MinIO Security)
- **Kiểm tra File nghiêm ngặt:**
  - Không tin tưởng phần mở rộng (extension) từ client. Bắt buộc kiểm tra MIME Type thực tế thông qua Magic Bytes (Apache Tika).
  - Giới hạn kích thước tệp tối đa (ví dụ: tối đa 50MB cho hồ sơ bản vẽ kỹ thuật, 10MB cho ảnh hiện trường).
  - Tên tệp lưu trữ trên MinIO/S3 phải được băm bằng UUID hoặc SHA-256, không sử dụng tên gốc từ client để tránh tấn công Path Traversal.
- **Cách ly và Truy cập An toàn:**
  - Tệp tải lên được lưu trong bucket riêng biệt, không cấp quyền public read cho toàn bộ bucket.
  - Quyền truy cập tệp phải thông qua Pre-signed URL có thời hạn ngắn (ví dụ: 15 - 30 phút) sau khi người dùng đã được xác thực và cấp quyền.

### 3.4 Bảo vệ Endpoint Quản trị (Admin Endpoints)
- Các endpoint quản trị (`/api/admin/**`, `/api/import/**`, `/actuator/**`) bắt buộc phải được bảo vệ bởi Spring Security với quyền `ROLE_ADMIN`.
- Cấm để lộ endpoint Actuator nhạy cảm (`env`, `beans`, `threaddump`) ra mạng công cộng. Chỉ cho phép `health` và `info`.

---

## 4. QUY TẮC XỬ LÝ LỖI (ERROR HANDLING RULES)

1. **Chuẩn hóa Error Response theo RFC 7807:** Toàn bộ API trả về lỗi phải tuân theo cấu trúc thống nhất (`API_CONVENTIONS.md`).
2. **Không làm rò rỉ vết lỗi (No Stack Trace Leaks):** Tuyệt đối không trả về raw exception, SQL error, hibernate trace hoặc class path cho client. Người dùng chỉ nhận được mã lỗi nghiệp vụ và thông điệp an toàn.
3. **Phân loại HTTP Status rõ ràng:** 200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500, 503.

---

## 5. QUY TẮC QUẢN LÝ PHIÊN BẢN VÀ COMMIT (GIT & REVIEW RULES)

### 5.1 Cấu trúc Thông điệp Commit (Conventional Commits)
```text
<type>(<scope>): <mô tả ngắn gọn bằng tiếng Việt hoặc tiếng Anh>

[Nội dung chi tiết nếu có]
[Liên kết Issue / Task nếu có]
```
Trong đó `type`: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`.

### 5.2 Quy tắc Review Bắt buộc Trước Khi Kết Thúc Task
1. Có tạo database, migration, bảng hoặc importer thứ hai không? (Không, đã tái sử dụng tài nguyên hiện hữu).
2. Có hard-code secret hoặc thông tin nhạy cảm không? (Không).
3. Migration Flyway có thể rollback hoặc chạy lại an toàn không? (Có).
4. Có nguy cơ SQL injection hoặc thiếu param binding không? (Không).
5. Frontend có truy cập trực tiếp bảng `raw_dataset_record` không? (Không, đi qua curated/ODS API).
6. Có test phủ trường hợp thành công và trường hợp biên với Testcontainers / Playwright không? (Có).
