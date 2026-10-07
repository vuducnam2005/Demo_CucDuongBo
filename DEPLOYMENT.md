# HƯỚNG DẪN TRIỂN KHAI MÔI TRƯỜNG SẢN XUẤT (DEPLOYMENT GUIDE)
## HỆ THỐNG CƠ SỞ DỮ LIỆU QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)

> **Cơ quan chủ quản**: Cục Đường bộ Việt Nam  
> **Phiên bản kiến trúc**: Modular Monolith 1.0.0 (Production Release)  
> **Ngày phát hành**: Tháng 10/2026  
> **Tiêu chuẩn áp dụng**: Nghị định 13/2023/NĐ-CP (Bảo vệ dữ liệu cá nhân), Thông tư 06/2023/TT-BTTTT (An toàn thông tin cấp độ 3).  
> **Nguyên tắc bảo mật**: *Tuyệt đối không ghi mật khẩu, token, khóa bí mật vào mã nguồn hoặc tài liệu công khai.*

---

## 1. TỔNG QUAN KIẾN TRÚC VẬN HÀNH SẢN XUẤT (TOPOLOGY)

Hệ thống được đóng gói và vận hành dựa trên công nghệ Docker Container hóa, quản trị phân tầng cô lập mạng:

```
[ Người dùng / Cán bộ Cục ĐBVN / Khu QLĐB ]
                    │
                    ▼  HTTPS :443 (TLS 1.2 / 1.3)
      ┌─────────────────────────────┐
      │     kcht_prod_nginx         │  (Edge Reverse Proxy, Rate Limiter, WAF Headers,
      │  (Nginx 1.27 Alpine)        │   SSL Termination, Static Cache, Gzip)
      └──────────────┬──────────────┘
                     │
         ┌───────────┴───────────┐
         │ (Internal Bridge Net) │
         ▼                       ▼
┌─────────────────┐     ┌──────────────────────────────────┐
│ kcht_prod_front │     │        kcht_prod_backend         │
│ (React 18 SPA)  │     │   (Spring Boot 3 + Java 21 LTS)  │
│ Port :80 (Int)  │     │       Port :8089 (Internal)      │
└─────────────────┘     └────────┬────────────────┬────────┘
                                 │                │
            ┌────────────────────┘                └────────────────────┐
            ▼                                                          ▼
┌───────────────────────────────┐              ┌───────────────────────────────┐
│       kcht_prod_postgres      │              │        kcht_prod_minio        │
│  (PostgreSQL 16 + PostGIS 3.4)│              │  (Object Storage Engine / S3) │
│     Port :5432 (Internal)     │              │     Port :9000 (Internal)     │
└───────────────────────────────┘              └───────────────────────────────┘
```

### Ranh giới an toàn mạng (Network Boundaries):
1. **Mạng ngoài (Public Internet)**: Chỉ mở 2 cổng duy nhất: **80 (HTTP redirect)** và **443 (HTTPS)** thông qua `kcht_prod_nginx`.
2. **Mạng nội bộ (Private Docker Networks)**:
   - `kcht_prod_frontend_net`: Kết nối Nginx Reverse Proxy và Frontend SPA.
   - `kcht_prod_backend_net`: Kết nối Nginx Reverse Proxy, Backend API, PostgreSQL, Flyway và MinIO.
3. **Cơ sở dữ liệu & Storage**: Không ánh xạ cổng 5432 và 9000 ra môi trường Internet công cộng, ngăn chặn 100% các cuộc tấn công rà quét cổng từ xa.

---

## 2. YÊU CẦU HẠ TẦNG VÀ PHẦN CỨNG MÁY CHỦ (PREREQUISITES)

### 2.1. Cấu hình phần cứng khuyến nghị (Server Sizing)
| Thành phần | Cấu hình tối thiểu (Staging) | Cấu hình khuyến nghị (Production) | Cấu hình mở rộng (High Scale) |
| :--- | :--- | :--- | :--- |
| **CPU** | 4 vCPUs (x86_64) | 8 vCPUs (Intel Xeon / AMD EPYC) | 16 vCPUs |
| **RAM** | 8 GB DDR4 | 16 GB DDR4/DDR5 | 32 GB - 64 GB |
| **Ổ cứng** | 100 GB SSD NVMe | 250 GB SSD NVMe Enterprise | 1 TB NVMe RAID 10 |
| **Hệ điều hành** | Ubuntu 22.04 LTS / RHEL 9 | Ubuntu 22.04/24.04 LTS (Kernel 6.x) | Red Hat Enterprise Linux 9 |
| **Băng thông** | 100 Mbps đối xứng | 1 Gbps đối xứng trong nước | 10 Gbps |

### 2.2. Phần mềm bắt buộc cài đặt trên máy chủ
```bash
# Cài đặt Docker Engine và Docker Compose v2 trên Ubuntu Server
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Xác minh phiên bản
docker --version         # Yêu cầu >= 24.0.0
docker compose version   # Yêu cầu >= 2.20.0
```

---

## 3. QUẢN TRỊ BẢO MẬT VÀ KHÓA BÍ MẬT (SECRET MANAGEMENT)

### 3.1. Nguyên tắc cốt lõi
- **Tuyệt đối không commit tệp chứa secret vào Git**: Tệp `.env.production` đã được đưa vào `.gitignore`.
- **Phân quyền truy cập tệp cấu hình**:
  ```bash
  chmod 600 .env.production
  sudo chown root:root .env.production
  ```
- **Sinh khóa ngẫu nhiên chuẩn mã hóa quân sự**:

```bash
# 1. Sinh mật khẩu CSDL PostgreSQL (32 ký tự ngẫu nhiên)
openssl rand -hex 16

# 2. Sinh mật mã root MinIO Object Storage (32 ký tự ngẫu nhiên)
openssl rand -hex 16

# 3. Sinh JWT Secret Key (HMAC-SHA256, 64 ký tự Base64)
openssl rand -base64 64
```

### 3.2. Cấu hình tệp biến môi trường `.env.production`
Tạo tệp `.env.production` từ bản mẫu [`.env.production.example`](file:///c:/Demo_CucDuongBo/.env.production.example):

```bash
cp .env.production.example .env.production
nano .env.production
```
Điền đầy đủ các giá trị sau khi đã sinh ngẫu nhiên:
- `POSTGRES_DB`: Tên CSDL (`kcht_prod_db`)
- `POSTGRES_USER`: Người dùng CSDL (`kcht_prod_admin`)
- `POSTGRES_PASSWORD`: Mật khẩu CSDL mạnh đã sinh
- `MINIO_ROOT_USER`: Tài khoản quản trị MinIO
- `MINIO_ROOT_PASSWORD`: Mật khẩu MinIO mạnh đã sinh
- `JWT_SECRET`: Khóa bí mật JWT Base64 64 ký tự
- `JWT_ACCESS_EXPIRATION_MS`: `900000` (15 phút)
- `JWT_REFRESH_EXPIRATION_MS`: `604800000` (7 ngày)

---

## 4. THIẾT LẬP CHỨNG CHỈ SỐ BẢO MẬT TLS / SSL

### 4.1. Môi trường Production (Chứng chỉ Let's Encrypt / Certbot)
```bash
# Cài đặt Certbot standalone để cấp phát chứng chỉ số miễn phí
sudo apt-get update && sudo apt-get install -y certbot

# Cấp phát chứng chỉ cho tên miền chính thức của Cục ĐBVN
sudo certbot certonly --standalone \
  -d kcht.drvn.gov.vn \
  --email quantri.kcht@drvn.gov.vn \
  --agree-tos --no-eff-email

# Sao chép chứng chỉ vào thư mục cấu hình Nginx
mkdir -p ./deploy/nginx/ssl
sudo cp /etc/letsencrypt/live/kcht.drvn.gov.vn/fullchain.pem ./deploy/nginx/ssl/kcht_tls.crt
sudo cp /etc/letsencrypt/live/kcht.drvn.gov.vn/privkey.pem ./deploy/nginx/ssl/kcht_tls.key
sudo chmod 644 ./deploy/nginx/ssl/kcht_tls.crt
sudo chmod 600 ./deploy/nginx/ssl/kcht_tls.key
```

### 4.2. Môi trường Staging / Diễn tập nội bộ (Self-Signed TLS)
Nếu triển khai tại mạng LAN nội bộ hoặc diễn tập, sử dụng script sinh tự động:
```bash
# Trên Linux/macOS:
bash ./deploy/nginx/ssl/generate-self-signed.sh

# Hoặc dùng Docker Openssl một dòng:
docker run --rm -v "${PWD}/deploy/nginx/ssl:/ssl" alpine/openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 -keyout /ssl/kcht_tls.key -out /ssl/kcht_tls.crt \
  -subj "/C=VN/ST=HaNoi/L=HaNoi/O=CucDuongBoVietNam/OU=KCHT/CN=kcht.drvn.gov.vn"
```

---

## 5. QUY TRÌNH THỰC THI TRIỂN KHAI TỪNG BƯỚC (STEP-BY-STEP DEPLOYMENT)

### Bước 1: Kiểm tra cấu hình và mã nguồn
```bash
cd /opt/kcht/Demo_CucDuongBo

# Kiểm tra tệp môi trường tồn tại
ls -l .env.production
ls -l deploy/nginx/ssl/kcht_tls.crt
ls -l deploy/nginx/ssl/kcht_tls.key
```

### Bước 2: Build và Khởi động Tầng Hạ tầng CSDL & Storage
Khởi động CSDL PostgreSQL, PostGIS và MinIO trước để hệ thống sẵn sàng tiếp nhận kết nối:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production up -d postgres minio minio-init
```
Kiểm tra trạng thái sẵn sàng của hạ tầng:
```bash
docker compose -f docker-compose.prod.yml ps
# Chờ cho đến khi postgres và minio chuyển trạng thái (healthy)
```

### Bước 3: Thực thi Schema Migration sản xuất (Flyway)
Chạy container Flyway tự động áp dụng 11 tệp migration từ V1 đến V11 (bao gồm tối ưu hóa B-Tree, Trigram GIN, PostGIS spatial extensions và projection cache WebGIS):
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm flyway
```
*Kết quả xác nhận*: Flyway hiển thị `Successfully applied 11 migrations` và thoát với mã `0`.

### Bước 4: Đóng gói và Khởi chạy Ứng dụng Backend & Frontend
```bash
# Đóng gói và khởi động các container ứng dụng
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build backend frontend nginx
```

### Bước 5: Kiểm tra sức khỏe toàn hệ thống (Health Check Verification)
```bash
# 1. Kiểm tra trạng thái toàn bộ 5 dịch vụ sản xuất
docker compose -f docker-compose.prod.yml ps

# 2. Kiểm tra Health probe từ Nginx Reverse Proxy
curl -k -I https://localhost/healthz
# Kỳ vọng: HTTP/1.1 200 OK

# 3. Kiểm tra Actuator Readiness probe của Backend
curl -k https://localhost/actuator/health
# Kỳ vọng: {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}

# 4. Kiểm tra trang chủ WebGIS Frontend
curl -k -I https://localhost/
# Kỳ vọng: HTTP/1.1 200 OK (Content-Type: text/html)
```

---

## 6. QUY TRÌNH CẬP NHẬT KHÔNG GIÁN ĐOẠN (ZERO-DOWNTIME ROLLING UPDATE)

Khi có bản vá lỗi hoặc tính năng mới cần release lên môi trường sản xuất:

```bash
# 1. Tải mã nguồn mới nhất từ nhánh release
git fetch origin
git checkout tags/v1.1.0

# 2. Tạo bản snapshot sao lưu CSDL phòng ngừa trước khi cập nhật
bash ./deploy/scripts/backup-postgres.sh

# 3. Kiểm tra và áp dụng migration mới (nếu có)
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm flyway migrate

# 4. Rebuild image backend và frontend mới
docker compose -f docker-compose.prod.yml --env-file .env.production build backend frontend

# 5. Khởi động lại theo cơ chế cuốn chiếu
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --no-deps backend
# Chờ container backend chuyển sang trạng thái healthy (30s)

docker compose -f docker-compose.prod.yml --env-file .env.production up -d --no-deps frontend
docker compose -f docker-compose.prod.yml --env-file .env.production exec nginx nginx -s reload

# 6. Xác nhận sau cập nhật
curl -k https://localhost/actuator/health
```

---

## 7. KẾ HOẠCH RÚT LUI KHẨN CẤP (EMERGENCY ROLLBACK)

Nếu sau khi triển khai phát sinh sự cố nghiêm trọng (Lỗi 500 diện rộng, hỏng dữ liệu):

1. **Khôi phục Container phiên bản trước**:
   ```bash
   docker compose -f docker-compose.prod.yml down backend frontend
   # Khởi động lại tag image ổn định trước đó
   docker tag kcht-backend:1.0.0-backup kcht-backend:1.0.0
   docker compose -f docker-compose.prod.yml up -d backend frontend
   ```
2. **Khôi phục Cơ sở dữ liệu về trạng thái trước deploy**:
   ```bash
   bash ./deploy/scripts/restore-postgres.sh ./deploy/backups/postgres/kcht_db_pre_deploy_snapshot.dump --force
   ```
3. **Làm mới bộ nhớ đệm Nginx & Client**:
   ```bash
   docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
   ```
