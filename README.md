# HỆ THỐNG QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam - Bộ Giao thông Vận tải  
**Kiến trúc:** Modular Monolith (Spring Boot 3 + Java 21) & Frontend SPA (React + TypeScript + Vite + Ant Design)  
**Cơ sở Dữ liệu & Lưu trữ:** PostgreSQL 16 + PostGIS 3.4+, MinIO Object Storage (S3-compatible)

---

## 1. KHỞI CHẠY HỆ THỐNG CỤC BỘ BẰNG MỘT LỆNH (ONE-COMMAND LOCAL STARTUP)

### Bước 1: Chuẩn bị biến môi trường
Sao chép tệp cấu hình mẫu `.env.example` thành `.env` (tuyệt đối không commit tệp `.env` chứa mật khẩu thật vào git):
```bash
cp .env.example .env
```

### Bước 2: Khởi động toàn bộ hạ tầng Docker Compose
Chỉ cần chạy một lệnh duy nhất để khởi động **PostgreSQL 16 + PostGIS**, tự động chạy **Flyway Migration**, khởi chạy **MinIO Object Storage** và tự động tạo các bucket (`kcht-documents`, `kcht-media`):
```bash
docker compose up -d
```

Kiểm tra trạng thái các container:
```bash
docker compose ps
```
*Kỳ vọng:* `kcht_postgres` ở trạng thái `Up (healthy)`, `kcht_minio` ở trạng thái `Up (healthy)`.

---

## 2. KHỞI CHẠY BACKEND SERVICE (SPRING BOOT 3 + JAVA 21)

Backend cung cấp hệ thống REST API, WebGIS GeoJSON endpoints, và Actuator Health check.

### Chạy ứng dụng Web API:
- **Trên Windows PowerShell:**
  ```powershell
  .\mvnw.bat spring-boot:run
  ```
- **Trên Linux / macOS:**
  ```bash
  ./mvnw spring-boot:run
  ```

### Các Endpoint Nền tảng:
- **Health Check:** `http://localhost:8089/actuator/health` (Phản hồi `{"status": "UP"}`)
- **Swagger UI (OpenAPI 3):** `http://localhost:8089/swagger-ui/index.html`
- **OpenAPI JSON Spec:** `http://localhost:8089/v3/api-docs`
- **Xác thực & Phiên (Auth):** `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`
- **Quản trị Người dùng (RBAC):** `GET /api/auth/users`, `POST /api/auth/users`
- **Nhật ký Kiểm toán (Audit):** `GET /api/audit-logs`
- **Danh mục 658 Datasets:** `http://localhost:8089/api/datasets`
- **MinIO Console:** `http://localhost:9011` (User: `kcht_minio_user`)

### Tài khoản Mẫu Đăng nhập (Mật khẩu mặc định):
- **Quản trị viên (`ROLE_ADMIN`):** `admin` / `<cấp qua kênh bảo mật>`
- **Lãnh đạo (`ROLE_MANAGER`):** `manager_demo` / `<cấp qua kênh bảo mật>`
- **Chuyên viên Kỹ thuật (`ROLE_EDITOR`):** `editor_demo` / `<cấp qua kênh bảo mật>`
- **Cán bộ Tra cứu (`ROLE_VIEWER`):** `viewer_demo` / `<cấp qua kênh bảo mật>`

---

## 3. KHỞI CHẠY IMPORT WORKER ĐỘC LẬP (INDEPENDENT WORKER)

Tác vụ nạp dữ liệu lớn được thiết kế tách biệt hoàn toàn với Web Request, sử dụng cơ chế Streaming Jackson $O(1)$ RAM, batch 500 bản ghi, có checkpoint và resume:

### Các lệnh nạp dữ liệu độc lập:
1. **Kiểm tra mô phỏng (Dry-run không ghi CSDL):**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--dry-run --file=assets/duonggom.json"
   ```
2. **Đồng bộ từ điển trường & metadata từ manifest.json:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--sync-registry"
   ```
3. **Nạp tệp cụ thể:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--file=assets/tbl_bridge.json"
   ```
4. **Nạp TOÀN BỘ 658 tệp dữ liệu:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--all"
   ```

---

## 4. KHỞI CHẠY FRONTEND (REACT + TYPESCRIPT + VITE + ANT DESIGN)

Giao diện người dùng được tổ chức trong thư mục `frontend/`.

```bash
cd frontend
npm install
npm run dev
```
Truy cập giao diện Web: `http://localhost:3000`  
Vite dev server đã được cấu hình proxy tự động chuyển tiếp các request `/api` và `/actuator` sang backend cổng `8089`.

---

## 5. BỘ LỆNH KIỂM THỬ, LINT VÀ FORMAT (TESTING, LINT & FORMAT)

### Kiểm thử Backend:
Chạy toàn bộ 25 bài kiểm thử đơn vị, kiểm thử tích hợp PostGIS và kiểm tra `/actuator/health`:
```powershell
.\mvnw.bat test
```

### Kiểm thử và Build Frontend:
```bash
cd frontend
# Kiểm tra Typecheck và Build Production
npm run build

# Định dạng mã nguồn (Format)
npm run format

# Kiểm tra quy chuẩn mã nguồn (Lint)
npm run lint
```

### Quy trình Tự động hóa CI (GitHub Actions):
Quy trình CI được cấu hình tự động tại `.github/workflows/ci.yml`, kích hoạt mỗi khi có `push` hoặc `pull_request` lên nhánh `main`, `master`, `develop`:
1. **Backend Job:** Cài đặt JDK 21 (Temurin), chạy toàn bộ unit & integration tests qua `./mvnw test`.
2. **Frontend Job:** Cài đặt Node.js 20, kiểm tra typecheck và build bundle Vite.

---

## 6. QUY TẮC KIẾN TRÚC VÀ TÀI LIỆU NGUỒN SỰ THẬT

- [`AI_RULES.md`](file:///c:/Demo_CucDuongBo/AI_RULES.md): Quy tắc coding, bảo mật, không làm mất dữ liệu, commit và review.
- [`AI_WORKFLOW.md`](file:///c:/Demo_CucDuongBo/AI_WORKFLOW.md): Quy trình thực thi theo task và lộ trình 15 giai đoạn.
- [`ARCHITECTURE.md`](file:///c:/Demo_CucDuongBo/ARCHITECTURE.md): Kiến trúc tổng thể, 14 đường lui và 6 quyết định bắt buộc.
- [`DATA_CONTRACT.md`](file:///c:/Demo_CucDuongBo/DATA_CONTRACT.md): Hợp đồng dữ liệu và quy chuẩn bàn giao Database sang Code.
- [`API_CONVENTIONS.md`](file:///c:/Demo_CucDuongBo/API_CONVENTIONS.md): Quy chuẩn REST API và cấu trúc phản hồi lỗi RFC 7807.
- [`DEFINITION_OF_DONE.md`](file:///c:/Demo_CucDuongBo/DEFINITION_OF_DONE.md): Checklist định nghĩa hoàn thành task.
- [`docs/architecture/MODULE_DEPENDENCY_RULES.md`](file:///c:/Demo_CucDuongBo/docs/architecture/MODULE_DEPENDENCY_RULES.md): Ma trận phụ thuộc được phép giữa 7 module nghiệp vụ.
- [`docs/reference/site-inventory.md`](file:///c:/Demo_CucDuongBo/docs/reference/site-inventory.md): Bảng tổng mục tính năng và phân kỳ MVP.
- [`docs/reference/route-map.md`](file:///c:/Demo_CucDuongBo/docs/reference/route-map.md): Bản đồ điều hướng và tuyến đường dẫn.
- [`docs/reference/dataset-map.md`](file:///c:/Demo_CucDuongBo/docs/reference/dataset-map.md): Ánh xạ 658 tập dữ liệu KCHT.
- [`docs/reference/role-permission-matrix.md`](file:///c:/Demo_CucDuongBo/docs/reference/role-permission-matrix.md): Ma trận phân quyền 4 vai trò (RBAC).
- [`docs/reference/ui-state-matrix.md`](file:///c:/Demo_CucDuongBo/docs/reference/ui-state-matrix.md): Ma trận trạng thái giao diện UI (Loading, Empty, Error).
- [`docs/reference/open-questions.md`](file:///c:/Demo_CucDuongBo/docs/reference/open-questions.md): Danh mục câu hỏi mở cần chốt.
