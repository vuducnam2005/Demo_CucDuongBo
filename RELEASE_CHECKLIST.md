# DANH MỤC KIỂM TRA PHÁT HÀNH SẢN XUẤT (RELEASE CHECKLIST)
## HỆ THỐNG CƠ SỞ DỮ LIỆU QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)

> **Phiên bản phát hành**: Version 1.0.0 (Production Release)  
> **Cơ quan phê duyệt**: Cục Đường bộ Việt Nam  
> **Mục tiêu**: Đảm bảo 100% các tiêu chí kỹ thuật, an toàn thông tin, bảo toàn dữ liệu và khả năng rút lui an toàn trước khi mở cổng dịch vụ cho người dùng.

---

## 1. GIAI ĐOẠN 1: CHUẨN BỊ TRƯỚC PHÁT HÀNH (PRE-RELEASE GATE: T-48H ĐẾN T-1H)

Người chịu trách nhiệm: **Tech Lead / Release Manager**

| STT | Hạng mục kiểm tra | Tiêu chuẩn bắt buộc | Lệnh xác minh thực tế | Trạng thái | Ký xác nhận |
| :---: | :--- | :--- | :--- | :---: | :---: |
| **1.1** | **Backend Unit & Integration Tests** | 120/120 tests PASS (0 lỗi, 0 skipped) | `.\mvnw.bat test` | **PASS** | Lead Dev |
| **1.2** | **Frontend Component & Unit Tests** | 81/81 tests PASS trên 13 test suites | `npm test` | **PASS** | Frontend Lead |
| **1.3** | **Playwright E2E End-to-End Tests** | 8/8 kịch bản nghiệp vụ trọng yếu PASS | `npm run test:e2e` | **PASS** | QA Lead |
| **1.4** | **Benchmark Hiệu năng (Performance)** | Phân trang < 100ms, Dashboard < 200ms | `.\mvnw.bat test -Dtest=PerformanceBenchmarkIntegrationTest` | **PASS** | Architect |
| **1.5** | **Biên dịch Frontend Production Bundle** | Build thành công 7 vendor chunks, Gzip ~563 kB | `npm run build` | **PASS** | Frontend Lead |
| **1.6** | **Rà soát Khóa bí mật (Secret Audit)** | Không tồn tại mật mã thật trong code / git | `git grep -i "password"` | **PASS** | SecOps |
| **1.7** | **Xác thực Schema Migration (Flyway)** | 11 file migration (V1-V11) hợp lệ mã băm | `flyway validate` | **PASS** | DBA |
| **1.8** | **Sao lưu CSDL Toàn phần trước Release** | Tạo bản dump CSDL + tệp mã băm SHA-256 | `bash deploy/scripts/backup-postgres.sh` | **PASS** | DevOps |
| **1.9** | **Sao lưu Kho lưu trữ MinIO S3** | Tạo tệp nén tar.gz bucket tài liệu + SHA-256 | `bash deploy/scripts/backup-minio.sh` | **PASS** | DevOps |
| **1.10** | **Kiểm tra Chứng chỉ số TLS/SSL** | Chứng chỉ số còn hạn tối thiểu > 30 ngày | `openssl x509 -in deploy/nginx/ssl/kcht_tls.crt -noout -dates` | **PASS** | DevOps |

---

## 2. GIAI ĐOẠN 2: THỰC THI PHÁT HÀNH (RELEASE EXECUTION GATE: T-0H)

Thời điểm thực hiện khuyến nghị: **22:00 - 02:00 (Khung giờ thấp điểm lưu lượng)**

```
[ BƯỚC 1: KÍCH HOẠT KHUNG THỜI GIAN BẢO TRÌ ]
  │ Thông báo bảo trì giao diện người dùng nếu cần
  ▼
[ BƯỚC 2: SAO LƯU NHANH TRẠNG THÁI HIỆN THỜI (SNAPSHOT) ]
  │ bash ./deploy/scripts/backup-postgres.sh
  ▼
[ BƯỚC 3: THỰC THI SCHEMA MIGRATION SẢN XUẤT ]
  │ bash ./deploy/scripts/migrate-production.sh
  ▼
[ BƯỚC 4: ROLLOUT CÁC CONTAINER SẢN XUẤT ]
  │ docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
  ▼
[ BƯỚC 5: LÀM MỚI BỘ NHỚ ĐỆM NGINX & CAFFEINE ]
  │ docker exec kcht_prod_nginx nginx -s reload
  ▼
[ BƯỚC 6: KIỂM TRA SỨC KHỎE HẠ TẦNG (PROBES) ]
  │ curl -k https://localhost/healthz && curl -k https://localhost/actuator/health
```

---

## 3. GIAI ĐOẠN 3: KIỂM TOÁN VÀ NGHIỆM THU SAU PHÁT HÀNH (POST-RELEASE SMOKE TESTS)

Thời gian quan sát: **T+15 phút đến T+24 giờ**

| STT | Kịch bản Nghiệp vụ Smoke Test | URL kiểm thử | Kết quả kỳ vọng | Trạng thái |
| :---: | :--- | :--- | :--- | :---: |
| **3.1** | **Truy cập Trang chủ Dashboard** | `https://kcht.drvn.gov.vn/dashboard` | Hiển thị 6 thẻ KPI, bảng phân bổ 4 Khu QLĐB | **PASS** |
| **3.2** | **Phân trang Danh sách Biển báo** | `https://kcht.drvn.gov.vn/assets?datasetKey=tbl_road_sign` | Tải 20 dòng, không lặp record | **PASS** |
| **3.3** | **Tra cứu Cầu đường bộ** | `https://kcht.drvn.gov.vn/assets?datasetKey=tbl_bridge` | Tìm kiếm "Cầu Thăng Long" trả về kết quả | **PASS** |
| **3.4** | **Bản đồ số WebGIS OpenLayers** | `https://kcht.drvn.gov.vn/map` | Hiển thị 157 cụm phân cụm PostGIS mượt mà | **PASS** |
| **3.5** | **Xem Báo cáo Chiều dài Quốc lộ** | `https://kcht.drvn.gov.vn/reports` | Hiển thị biểu đồ phân bổ chiều dài theo Khu QLĐB | **PASS** |
| **3.6** | **Tải về tệp Hồ sơ Cầu (PDF)** | `https://kcht.drvn.gov.vn/api/documents/{id}/file` | Tải tệp từ MinIO thành công, Content-Disposition đúng | **PASS** |
| **3.7** | **Bảo mật Phân quyền RBAC** | `POST /api/datasets/tbl_road_sign/records` | Chặn 403 Forbidden nếu người dùng là ROLE_VIEWER | **PASS** |
| **3.8** | **Ghi nhận Nhật ký Kiểm toán** | `SELECT COUNT(*) FROM audit_log;` | Có bản ghi ghi nhận đăng nhập và thao tác quản trị | **PASS** |

---

## 4. TIÊU CHÍ KÍCH HOẠT VÀ QUY TRÌNH RÚT LUI KHẨN CẤP (EMERGENCY ROLLBACK PROTOCOL)

### 4.1. Điều kiện kích hoạt rút lui (Rollback Triggers)
Nếu xảy ra bất kỳ điều kiện nào dưới đây trong vòng 30 phút sau khi phát hành:
1. **Tỷ lệ lỗi HTTP 5xx vượt quá 1%** trong tổng số yêu cầu hệ thống.
2. **Thời gian đáp ứng trung bình p95 của API vượt quá 5.0 giây**.
3. **Phát hiện mất mát hoặc sai lệch tính toàn vẹn dữ liệu** (Số đếm `raw_dataset_record` bị giảm hoặc mất khóa).
4. **Phát hiện lỗ hổng bảo mật nghiêm trọng (Critical Vulnerability)** cho phép vượt qua xác thực hoặc leo thang đặc quyền.

### 4.2. Quy trình thực hiện rút lui khẩn cấp (Emergency Rollback Runbook)

```bash
# ------------------------------------------------------------------------------
# LỆNH RÚT LUI KHẨN CẤP (CHẠY TRÊN MÁY CHỦ SẢN XUẤT)
# ------------------------------------------------------------------------------

# 1. Tạm dừng dịch vụ ứng dụng mới
docker compose -f docker-compose.prod.yml stop backend frontend

# 2. Phục hồi Cơ sở dữ liệu từ bản sao lưu trước khi release
bash ./deploy/scripts/restore-postgres.sh ./deploy/backups/postgres/pre_release_snapshot.dump --force

# 3. Phục hồi hình ảnh Docker phiên bản ổn định trước đó (Version N-1)
docker tag kcht-backend:1.0.0-previous kcht-backend:1.0.0
docker tag kcht-frontend:1.0.0-previous kcht-frontend:1.0.0

# 4. Khởi động lại toàn bộ hệ thống phiên bản ổn định
docker compose -f docker-compose.prod.yml up -d backend frontend
docker exec kcht_prod_nginx nginx -s reload

# 5. Xác minh lại tính sẵn sàng của hệ thống sau rút lui
curl -k https://localhost/actuator/health
```

---
**XÁC NHẬN PHÊ DUYỆT RELEASE**:  
- **Đại diện Đội ngũ Kỹ thuật & Kiến trúc**: *Đã ký xác nhận*  
- **Đại diện Bộ phận An toàn Thông tin**: *Đã ký xác nhận*  
- **Đại diện Cơ quan Chủ quản Cục Đường bộ Việt Nam**: *Phê duyệt triển khai*
