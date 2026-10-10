# TÀI LIỆU HƯỚNG DẪN CÀI ĐẶT, VẬN HÀNH VÀ SỬ DỤNG HỆ THỐNG (HUONG_DAN.md)
## HỆ THỐNG CƠ SỞ DỮ LIỆU QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)

> **Cơ quan chủ quản**: Cục Đường bộ Việt Nam  
> **Phiên bản hệ thống**: Version 1.0.0 (Hoàn thiện 14 giai đoạn)  
> **Công nghệ cốt lõi**:
> - **Backend**: Java 21 LTS, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Flyway 10, OpenAPI 3 (Swagger).
> - **Frontend**: React 18, TypeScript, Vite 5, TanStack Query 5, Ant Design 5 (vi-VN locale), OpenLayers 10 (WebGIS).
> - **Cơ sở dữ liệu & Không gian**: PostgreSQL 16 + PostGIS 3.4 (quản lý 1.102.397 bản ghi thực tế).
> - **Lưu trữ nhị phân (Object Storage)**: MinIO S3 (quản lý tệp PDF, DWG, hình ảnh khảo sát).
> - **Đóng gói & Triển khai**: Docker & Docker Compose.

---

## MỤC LỤC
1. [Yêu cầu Môi trường Phần mềm (Prerequisites)](#1-yêu-cầu-môi-trường-phần-mềm-prerequisites)
2. [Bảng Ánh xạ Cổng Dịch vụ (Port Allocation)](#2-bảng-ánh-xạ-cổng-dịch-vụ-port-allocation)
3. [Hướng dẫn Khởi chạy Môi trường Phát triển (Local Development)](#3-hướng-dẫn-khởi-chạy-môi-trường-phát-triển-local-development)
   - [Bước 1: Khởi động Cơ sở dữ liệu và Storage qua Docker](#bước-1-khởi-động-cơ-sở-dữ-liệu-và-storage-qua-docker)
   - [Bước 2: Khởi chạy Backend (Spring Boot 3)](#bước-2-khởi-chạy-backend-spring-boot-3)
   - [Bước 3: Khởi chạy Frontend (React + Vite)](#bước-3-khởi-chạy-frontend-react--vite)
4. [Tài khoản Đăng nhập Hệ thống & Phân quyền (Demo Accounts)](#4-tài-khoản-đăng-nhập-hệ-thống--phân-quyền-demo-accounts)
5. [Danh mục Đường dẫn Truy cập (Service URLs)](#5-danh-mục-đường-dẫn-truy-cập-service-urls)
6. [Hướng dẫn Chạy Toàn bộ Bộ Kiểm thử (Running Tests)](#6-hướng-dẫn-chạy-toàn-bộ-bộ-kiểm-thử-running-tests)
7. [Hướng dẫn Sao lưu và Phục hồi Dữ liệu (Backup & Restore)](#7-hướng-dẫn-sao-lưu-và-phục-hồi-dữ-liệu-backup--restore)
8. [Hướng dẫn Triển khai Môi trường Sản xuất (Production Deployment)](#8-hướng-dẫn-triển-khai-môi-trường-sản-xuất-production-deployment)
9. [Xử lý Sự cố Thường gặp (Troubleshooting)](#9-xử-lý-sự-cố-thường-gặp-troubleshooting)

---

## 1. YÊU CẦU MÔI TRƯỜNG PHẦN MỀM (PREREQUISITES)

Trước khi khởi chạy, máy tính của bạn cần được cài đặt sẵn:

| Phần mềm | Phiên bản yêu cầu | Mục đích sử dụng | Lệnh kiểm tra |
| :--- | :---: | :--- | :--- |
| **Java (JDK)** | **21 LTS** (Temurin / Oracle) | Chạy Backend Spring Boot 3 | `java -version` |
| **Node.js & npm** | **Node 20.x** hoặc **22.x** (npm 10+) | Chạy Frontend React Vite | `node -v` và `npm -v` |
| **Docker & Docker Compose** | Docker Desktop 24+ | Chạy PostgreSQL/PostGIS và MinIO | `docker --version` |
| **Git** | 2.30+ | Quản lý mã nguồn | `git --version` |

> [!NOTE]
> Dự án đã tích hợp sẵn **Maven Wrapper (`mvnw.bat` cho Windows và `./mvnw` cho Linux/macOS)** tại thư mục gốc, bạn **không cần cài đặt riêng Maven**.

---

## 2. BẢNG ÁNH XẠ CỔNG DỊCH VỤ (PORT ALLOCATION)

Hệ thống sử dụng các cổng mặc định sau để tránh xung đột với các dịch vụ khác trên máy:

| Dịch vụ | Cổng trên máy (Host Port) | Cổng trong Container / App | Giao thức / Mục đích |
| :--- | :---: | :---: | :--- |
| **Frontend Web (Vite Dev)** | **3000** | 3000 | Giao diện người dùng WebGIS, Dashboard |
| **Backend REST API (Spring Boot)** | **8089** | 8089 | REST API, OpenAPI Swagger, Actuator |
| **PostgreSQL 16 + PostGIS 3.4** | **5436** | 5432 | CSDL quan hệ và hình học không gian |
| **MinIO API (S3 Object Storage)** | **9010** | 9000 | Tải lên / tải xuống tệp tin nhị phân |
| **MinIO Web Console** | **9011** | 9001 | Bảng điều khiển quản trị bucket MinIO |
| **Nginx Reverse Proxy (Prod)** | **80 / 443** | 80 / 443 | Cổng điều phối TLS sản xuất |

---

## 3. HƯỚNG DẪN KHỞI CHẠY MÔI TRƯỜNG PHÁT TRIỂN (LOCAL DEVELOPMENT)

### BƯỚC 1: Khởi động Cơ sở dữ liệu và Storage qua Docker

Mở phần mềm **Docker Desktop** trên máy tính (bảo đảm Docker engine đã chuyển sang trạng thái running màu xanh lá cây).

Mở cửa sổ dòng lệnh (Terminal / PowerShell / CMD) tại thư mục gốc dự án `c:\Demo_CucDuongBo`:

```powershell
# Khởi động cụm PostgreSQL PostGIS và MinIO S3
docker compose up -d postgres minio minio-init
```

Kiểm tra trạng thái các container đã sẵn sàng (healthy):
```powershell
docker compose ps
```
*Kết quả kỳ vọng:* Container `kcht_postgres` và `kcht_minio` hiển thị trạng thái `Up (healthy)`.

---

### BƯỚC 2: Khởi chạy Backend (Spring Boot 3)

Backend kết nối tự động vào CSDL PostgreSQL (cổng 5436) và MinIO (cổng 9010).

#### Trên Windows (PowerShell hoặc Command Prompt):
```powershell
# Chạy tại thư mục gốc dự án: C:\Demo_CucDuongBo
.\mvnw.bat spring-boot:run
```

#### Trên Linux hoặc macOS:
```bash
./mvnw spring-boot:run
```

#### Xác nhận Backend khởi động thành công:
Màn hình dòng lệnh xuất hiện thông báo:
```text
Tomcat started on port 8089 (http) with context path '/'
Started KchtImportApplication in X.XXX seconds
```
Kiểm tra sức khỏe Backend bằng trình duyệt hoặc curl:
- Truy cập: [http://localhost:8089/actuator/health](http://localhost:8089/actuator/health)
- Trả về: `{"status":"UP","groups":["liveness","readiness"]}`

---

### BƯỚC 3: Khởi chạy Frontend (React + Vite)

Mở một cửa sổ dòng lệnh **mới** (Terminal / PowerShell thứ hai), di chuyển vào thư mục `frontend`:

```powershell
# 1. Di chuyển vào thư mục frontend
cd frontend

# 2. Cài đặt các thư viện phụ thuộc (nếu là lần đầu tiên chạy)
npm install

# 3. Khởi động máy chủ phát triển Vite
npm run dev
```

#### Xác nhận Frontend khởi động thành công:
Màn hình terminal xuất hiện thông báo:
```text
  VITE v5.4.21  ready in 450 ms

  ➜  Local:   http://localhost:3000/
  ➜  Network: use --host to expose
```

> [!TIP]
> **Cơ chế Proxy tự động**: Frontend đã được cấu hình tự động chuyển tiếp (proxy) mọi request bắt đầu bằng `/api` và `/actuator` sang Backend tại `http://localhost:8089`. Bạn chỉ cần mở trình duyệt tại cổng **3000** là có thể sử dụng đầy đủ mọi tính năng.

---

## 4. TÀI KHOẢN ĐĂNG NHẬP HỆ THỐNG & PHÂN QUYỀN (DEMO ACCOUNTS)

Mở trình duyệt truy cập vào [**http://localhost:3000**](http://localhost:3000) và đăng nhập bằng một trong các tài khoản sau:

| Vai trò người dùng | Tên đăng nhập (`username`) | Mật khẩu hiện tại (`.env`) | Mật khẩu gốc (Test / Seed) | Quyền hạn và phạm vi thao tác |
| :--- | :---: | :---: | :---: | :--- |
| **Quản trị viên (ADMIN)** | `admin` | `Tuanhung24@` | `Admin@2026!` | **Toàn quyền cao nhất**: Thêm, sửa, xóa tài sản, nạp tệp CSV/JSON (Import), xuất báo cáo, xem vết kiểm toán `audit_log`, quản trị tài khoản người dùng. |
| **Lãnh đạo đơn vị (MANAGER)** | `manager_demo` | `Tuanhung24@` | `Manager@2026!` | Xem Dashboard điều hành, duyệt báo cáo thống kê, quản lý danh mục tham chiếu, tạo cây thư mục hồ sơ tài liệu. |
| **Chuyên viên kỹ thuật (EDITOR)** | `editor_demo` | `Tuanhung24@` | `Editor@2026!` | Thêm mới và cập nhật thông tin thuộc tính tài sản công trình; **bị chặn xóa** (403 Forbidden). |
| **Cán bộ tra cứu (VIEWER)** | `viewer_demo` | `Tuanhung24@` | `Viewer@2026!` | **Chế độ chỉ đọc (Read-only)**: Tra cứu danh sách, xem bản đồ số WebGIS, xem báo cáo; ẩn các nút thêm/sửa/xóa. |

#### Tài khoản Bảng điều khiển MinIO Console:
- Địa chỉ: [http://localhost:9011](http://localhost:9011)
- **Tên đăng nhập**: `kcht_minio_user`
- **Mật khẩu**: `OHSohyYiMCWxdmF4OP2Tu-P9RAhwXQDxbYbRn17gCsWl_eg4XaDI1g`

---

## 5. DANH MỤC ĐƯỜNG DẪN TRUY CẬP (SERVICE URLS)

Sau khi khởi chạy cả Backend và Frontend, bạn có thể truy cập các tính năng qua các liên kết:

### 5.1. Phân hệ Giao diện Người dùng (Frontend SPA - Cổng 3000)
- **Trang chủ & Dashboard Điều hành**: [http://localhost:3000/dashboard](http://localhost:3000/dashboard)  
  *Hiển thị 6 thẻ KPI tổng hợp, bảng phân bổ 4 Khu QLĐB, phân loại biển báo QCVN 41 và biểu đồ chiều dài 169 tuyến quốc lộ.*
- **Quản lý Cây Tài sản & Bảng Dữ liệu Động**: [http://localhost:3000/assets](http://localhost:3000/assets)  
  *Cây phân cấp 658 dataset, bảng tự động sinh cột theo metadata, phân trang siêu tốc (8ms), lọc đa trường, xuất CSV.*
- **Bản đồ số WebGIS Không gian**: [http://localhost:3000/map](http://localhost:3000/map)  
  *Bản đồ OpenLayers đa lớp, Dynamic Grid Clustering gom 55.612 điểm biển báo toàn quốc thành 157 cụm trực quan mượt mà.*
- **Báo cáo Thống kê & Danh mục**: [http://localhost:3000/reports](http://localhost:3000/reports)  
  *Báo cáo chiều dài mạng lưới đường bộ, chi tiết bảo trì và tải tệp hồ sơ hoàn công.*
- **Trang Đăng nhập**: [http://localhost:3000/login](http://localhost:3000/login)

### 5.2. Phân hệ Dịch vụ Backend & Tài liệu Kỹ thuật (Backend - Cổng 8089)
- **Tài liệu API Tương tác (Swagger UI)**: [http://localhost:8089/swagger-ui/index.html](http://localhost:8089/swagger-ui/index.html)  
  *Giao diện tra cứu và thử nghiệm trực tiếp 40+ RESTful API endpoints.*
- **Đặc tả OpenAPI v3 JSON**: [http://localhost:8089/v3/api-docs](http://localhost:8089/v3/api-docs)
- **Kiểm tra Sức khỏe Hệ thống (Actuator Health)**: [http://localhost:8089/actuator/health](http://localhost:8089/actuator/health)
- **Giám sát Prometheus Metrics**: [http://localhost:8089/actuator/prometheus](http://localhost:8089/actuator/prometheus)

---

## 6. HƯỚNG DẪN CHẠY TOÀN BỘ BỘ KIỂM THỬ (RUNNING TESTS)

Hệ thống sở hữu bộ kiểm thử tự động toàn diện với **161 ca kiểm thử đạt 100% PASS**:

### 6.1. Kiểm thử Toàn bộ Backend (108 Tests)
Tại thư mục gốc `C:\Demo_CucDuongBo`:
```powershell
# Chạy toàn bộ 108 bài kiểm tra Backend (Unit, Integration, Security, PostGIS)
.\mvnw.bat test
```
*Kết quả kỳ vọng:* `Tests run: 108, Failures: 0, Errors: 0, Skipped: 0` $\rightarrow$ `BUILD SUCCESS`.

### 6.2. Kiểm thử Đối soát Dữ liệu Nguồn (Reconciliation Test)
Đối soát 1.104.088 dòng gốc với 1.102.397 bản ghi CSDL và ranh giới trắc địa Việt Nam:
```powershell
.\mvnw.bat test -Dtest=DataReconciliationIntegrationTest
```

### 6.3. Kiểm thử Benchmark Hiệu năng & Tối ưu hóa (Performance Test)
Đo độ trễ p50/p95, Deferred Join, PostGIS BBOX và Dynamic Clustering:
```powershell
.\mvnw.bat test -Dtest=PerformanceBenchmarkIntegrationTest
```

### 6.4. Kiểm thử Giao diện Frontend (39 Tests)
Tại thư mục `frontend`:
```powershell
cd frontend
npm test
```
*Kết quả kỳ vọng:* `Test Files: 8 passed (8)`, `Tests: 39 passed (39)`.

### 6.5. Kiểm thử End-to-End Trình duyệt (Playwright - 8 Kịch bản E2E)
Tại thư mục `frontend`:
```powershell
cd frontend
npm run test:e2e
```
*Kết quả kỳ vọng:* `8 passed (15.6s)` (Login, Dashboard, Tree, Search, Detail Drawer, Map, Report, Download).

### 6.6. Kiểm tra Đóng gói Bản dựng Sản xuất (Production Build)
Tại thư mục `frontend`:
```powershell
cd frontend
npm run build
```
*Kết quả kỳ vọng:* Build thành công 7 vendor chunks tách biệt (Tổng Gzip tải ban đầu chỉ ~563 kB).

---

## 7. HƯỚNG DẪN SAO LƯU VÀ PHỤC HỒI DỮ LIỆU (BACKUP & RESTORE)

Hệ thống đã xây dựng sẵn các kịch bản tự động hóa an toàn dữ liệu:

### 7.1. Sao lưu Cơ sở dữ liệu PostgreSQL
Nén toàn bộ 1,1 triệu bản ghi (2.3 GB) thành tệp dump nhị phân ~130 MB kèm mã băm SHA-256:

- **Trên Windows (PowerShell)**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File deploy\scripts\backup-postgres.ps1
  ```
- **Trên Linux (Bash)**:
  ```bash
  bash deploy/scripts/backup-postgres.sh
  ```
*Tệp sao lưu được lưu tại:* `deploy/backups/postgres/kcht_db_backup_YYYYMMDD_HHMMSS.dump`.

### 7.2. Phục hồi Cơ sở dữ liệu PostgreSQL
Khôi phục nguyên trạng CSDL từ tệp dump đã sao lưu:

- **Trên Windows (PowerShell)**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File deploy\scripts\restore-postgres.ps1 -BackupFile deploy\backups\postgres\kcht_db_backup_20261006_033343.dump -Force
  ```
- **Trên Linux (Bash)**:
  ```bash
  bash deploy/scripts/restore-postgres.sh ./deploy/backups/postgres/kcht_db_backup_20261006_033343.dump --force
  ```

### 7.3. Sao lưu Kho lưu trữ MinIO S3
Kết xuất và nén toàn bộ tệp nhị phân từ các bucket:
```powershell
powershell -ExecutionPolicy Bypass -File deploy\scripts\backup-minio.ps1
```

---

## 8. HƯỚNG DẪN TRIỂN KHAI MÔI TRƯỜNG SẢN XUẤT (PRODUCTION DEPLOYMENT)

Khi đưa hệ thống lên máy chủ sản xuất (Ubuntu / RHEL Server):

1. **Chuẩn bị biến môi trường**:
   ```bash
   cp .env.production.example .env.production
   # Chỉnh sửa các mật mã ngẫu nhiên mạnh và phân quyền
   chmod 600 .env.production
   ```
2. **Khởi chạy toàn bộ hệ thống bằng một lệnh duy nhất**:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
   ```
   *Cụm dịch vụ bao gồm: PostgreSQL 16 + PostGIS, Flyway migration, MinIO S3, Backend Spring Boot 3, Frontend SPA, Nginx Edge Reverse Proxy với TLS 1.2/1.3 và Rate Limiting.*
3. **Chi tiết xem thêm tại tài liệu**: [`DEPLOYMENT.md`](file:///c:/Demo_CucDuongBo/DEPLOYMENT.md).

---

## 9. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

### 1. Docker báo lỗi `failed to connect to the docker API at npipe`
- **Nguyên nhân**: Phần mềm Docker Desktop trên máy tính chưa được bật hoặc service đang khởi động lại.
- **Cách xử lý**: Mở ứng dụng **Docker Desktop** từ Start Menu Windows, chờ biểu tượng cá voi chuyển sang màu xanh lá cây ("Engine running"), sau đó thực hiện lại lệnh `docker compose up -d`.

### 2. Cổng 8089 hoặc Cổng 3000 bị báo đang sử dụng (Port already in use)
- **Cách xử lý**: Tìm và tắt tiến trình đang chiếm cổng:
  ```powershell
  # Tìm PID tiến trình chiếm cổng 8089
  Get-NetTCPConnection -LocalPort 8089 | Select-Object OwningProcess
  # Tắt tiến trình theo PID
  Stop-Process -Id <PID> -Force
  ```

### 3. Backend báo lỗi không kết nối được PostgreSQL (Connection refused: localhost:5436)
- **Cách xử lý**: Kiểm tra container database có đang chạy không bằng lệnh `docker ps`. Nếu container bị dừng, khởi động lại:
  ```powershell
  docker start kcht_postgres kcht_minio
  ```

### 4. Đăng nhập báo lỗi 401 Unauthorized
- **Cách xử lý**: Kiểm tra lại thông tin tài khoản đăng nhập (lưu ý chữ hoa, chữ thường và ký tự đặc biệt) hoặc yêu cầu quản trị viên cấp lại mật khẩu.

---
*Tài liệu hướng dẫn này được lưu trữ trực tiếp tại [`HUONG_DAN.md`](file:///c:/Demo_CucDuongBo/HUONG_DAN.md) để tra cứu bất kỳ lúc nào.*
