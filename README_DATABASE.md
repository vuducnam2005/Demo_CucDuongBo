# HƯỚNG DẪN QUẢN TRỊ VÀ VẬN HÀNH DATABASE KCHT ĐƯỜNG BỘ
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Tổng quan Hạ tầng Cơ sở Dữ liệu

Hệ thống cơ sở dữ liệu được đóng gói và vận hành qua Docker Compose, tích hợp sẵn:
- **Hệ quản trị CSDL:** PostgreSQL 16
- **Tiện ích Không gian Địa lý:** PostGIS 3.4+
- **Quản lý Phiên bản Schema:** Flyway 10
- **Hệ quy chiếu Không gian:** WGS 84 (EPSG:4326)
- **Múi giờ Thống nhất:** UTC (`TZ=UTC`)
- **Khối lượng quản lý:** Hơn 1.1 triệu bản ghi thô và hơn 833,000 công trình tài sản hạ tầng đường bộ.

---

## 2. Cấu hình Môi trường (Environment Setup)

Hệ thống sử dụng tệp `.env` để bảo mật toàn bộ thông tin đăng nhập, không hard-code mật khẩu trong mã nguồn.

### 2.1 Khởi tạo tệp `.env`
Sao chép từ tệp mẫu `.env.example`:
```bash
cp .env.example .env
```

### 2.2 Các biến cấu hình chính trong `.env`
```env
# Thông số kết nối CSDL
POSTGRES_DB=kcht_db
POSTGRES_USER=kcht_user
POSTGRES_PASSWORD=<set in local .env, never commit>
POSTGRES_PORT=5436

# Múi giờ hệ thống (Bắt buộc UTC)
TZ=UTC
PGTZ=UTC

# Số lần thử kết nối của Flyway
FLYWAY_CONNECT_RETRIES=60
```
> [!NOTE]
> Cổng máy chủ mặc định được đặt là `5436` (thay vì `5432`) để tránh xung đột nếu máy phát triển đang chạy các dịch vụ PostgreSQL cục bộ khác.

---

## 3. Khởi động Cơ sở Dữ liệu và Chạy Migration Tự động

### 3.1 Khởi động toàn bộ dịch vụ (PostgreSQL + PostGIS + Flyway)
```bash
docker compose up -d
```

### 3.2 Cơ chế Hoạt động Tự động:
1. Docker Compose khởi động container `kcht_postgres`.
2. Kiểm tra sức khỏe (`healthcheck`): PostgreSQL chạy lệnh `pg_isready` mỗi 5 giây.
3. Khi PostgreSQL đạt trạng thái **`healthy`**, container `kcht_flyway` tự động kích hoạt, kết nối vào database và thực thi toàn bộ các tệp migration trong thư mục [`db/migration/`](file:///c:/Demo_CucDuongBo/db/migration):
   - `V1__init_extensions_and_system.sql`: Kích hoạt PostGIS, pg_trgm, bảng bảo mật và kiểm toán.
   - `V2__init_raw_ingestion.sql`: Khởi tạo bảng raw ingestion, từ điển dữ liệu, registry và nhật ký lỗi.
   - `V3__init_curated_assets_and_geometry.sql`: Khởi tạo bảng tài sản `asset_record` và hình học `asset_geometry`.
   - `V4__init_reference_and_documents.sql`: Khởi tạo danh mục tham chiếu và quản lý hồ sơ tài liệu.
   - `V5__init_dynamic_views.sql`: Khởi tạo các View báo cáo bảo trì động.
4. Sau khi hoàn tất migration, container `kcht_flyway` sẽ tự dừng (`Exited (0)`) mà không chiếm dụng tài nguyên CPU/RAM.

---

## 4. Kiểm tra Trạng thái và Tiện ích Không gian PostGIS

### 4.1 Kiểm tra trạng thái Container
```bash
docker compose ps
```
Kết quả kỳ vọng: `kcht_postgres` ở trạng thái `Up (healthy)`.

### 4.2 Chạy Script Kiểm tra PostGIS
- **Trên Windows PowerShell:**
  ```powershell
  .\scripts\test_postgis.ps1
  ```
- **Trên Windows Command Prompt (CMD):**
  ```cmd
  .\scripts\test_postgis.bat
  ```
- **Nội dung kiểm tra:**
  - Phiên bản PostgreSQL 16 & PostGIS 3.4+.
  - Tiện ích mở rộng `postgis` và `pg_trgm`.
  - Múi giờ UTC (`SHOW timezone`).
  - Hàm dựng điểm `ST_MakePoint`, chuyển đổi sang `GeoJSON`.
  - Hàm tính khoảng cách và độ dài trắc địa theo mét (`ST_Length(geography)`).
  - Truy vấn giao cắt hộp bao không gian (`ST_Intersects` & `ST_MakeEnvelope`).
  - Tính năng tìm kiếm mờ tiếng Việt (`similarity` & `show_trgm`).

---

## 5. Sao lưu và Phục hồi Dữ liệu Cục bộ (Backup & Restore)

### 5.1 Sao lưu dữ liệu (Backup)
Tạo bản sao lưu định dạng SQL nén vào thư mục `backups/`:
- **PowerShell:**
  ```powershell
  .\scripts\backup_db.ps1
  ```
- **CMD:**
  ```cmd
  .\scripts\backup_db.bat
  ```

### 5.2 Phục hồi dữ liệu (Restore)
- **PowerShell:**
  ```powershell
  # Tự động lấy file backup mới nhất:
  .\scripts\restore_db.ps1
  
  # Hoặc chỉ định rõ file:
  .\scripts\restore_db.ps1 -FilePath ".\backups\kcht_db_backup_20261005_220000.sql"
  ```
- **CMD:**
  ```cmd
  .\scripts\restore_db.bat .\backups\kcht_db_backup_20261005_220000.sql
  ```

---

## 6. Thông số Kết nối Dành cho Backend Spring Boot 3

Trong tệp `application.yml` của ứng dụng Spring Boot 3:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5436/kcht_db?currentSchema=public&reWriteBatchedInserts=true
    username: kcht_user
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          order_inserts: true
          order_updates: true
```

---

## 7. Xử lý Sự cố Thường gặp (Troubleshooting)

| Tình huống sự cố | Nguyên nhân | Biện pháp xử lý |
| :--- | :--- | :--- |
| **Port 5436 already in use** | Cổng 5436 đã bị chiếm dụng bởi ứng dụng khác. | Mở `.env`, đổi `POSTGRES_PORT=5437` hoặc cổng trống khác, sau đó chạy lại `docker compose up -d`. |
| **Flyway Migration Failed** | Lỗi cú pháp SQL hoặc đụng độ khóa. | Chạy `docker logs kcht_flyway` để xem chi tiết lỗi. Sửa file migration trong `db/migration/` rồi chạy lại `docker compose up flyway`. |
| **Docker daemon not running** | Dịch vụ Docker Desktop chưa được bật. | Bật Docker Desktop trên Windows hoặc kiểm tra lệnh `docker info`. |
