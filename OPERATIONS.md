# SỔ TAY VẬN HÀNH VÀ BẢO TRÌ HỆ THỐNG (OPERATIONS MANUAL)
## HỆ THỐNG CƠ SỞ DỮ LIỆU QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)

> **Cơ quan**: Cục Đường bộ Việt Nam  
> **Bộ phận phụ trách**: Trung tâm Công nghệ Thông tin & Phòng Quản lý Bảo trì Kết cấu Hạ tầng  
> **Tài liệu chuẩn hóa**: Quy trình vận hành kỹ thuật (SOP - Standard Operating Procedure)  
> **Nguyên tắc**: *Giám sát chủ động, bảo trì định kỳ, xử lý sự cố có quy trình và bảo toàn dữ liệu tuyệt đối.*

---

## 1. QUY TRÌNH KIỂM TRA ĐẦU NGÀY (DAILY MORNING HEALTH CHECK)

Mỗi ngày làm việc vào lúc 07:30 - 08:00 sáng, kỹ sư quản trị hệ thống thực hiện tuần tự các bước kiểm tra sau:

```bash
# 1. Kiểm tra trạng thái sống của toàn bộ các container dịch vụ
docker compose -f docker-compose.prod.yml ps

# Tiêu chí đạt: Tất cả các container (postgres, minio, backend, frontend, nginx) đều có trạng thái "Up (healthy)"
```

```bash
# 2. Kiểm tra tài nguyên CPU và RAM máy chủ
docker stats --no-stream

# Ngưỡng an toàn:
# - backend: RAM < 1.5 GB (ngưỡng giới hạn 2 GB)
# - postgres: RAM < 3.0 GB (ngưỡng giới hạn 4 GB)
# - minio: RAM < 500 MB (ngưỡng giới hạn 1 GB)
# - nginx: RAM < 100 MB
```

```bash
# 3. Kiểm tra dung lượng ổ đĩa lưu trữ (Disk Free Space)
df -h /var/lib/docker
df -h /backups

# Cảnh báo: Nếu dung lượng sử dụng vượt quá 80%, phải tiến hành dọn dẹp log hoặc xoay vòng bản sao lưu theo Mục 6.
```

```bash
# 4. Kiểm tra mã lỗi HTTP 5xx trong access log Nginx 24 giờ qua
docker exec kcht_prod_nginx grep -c '"status_code":5' /var/log/nginx/access.log || echo "0"

# Tiêu chí đạt: Số lượng lỗi 5xx < 0.05% tổng số request.
```

---

## 2. GIÁM SÁT HIỆU NĂNG VÀ CẢNH BÁO (METRICS & PROMETHEUS MONITORING)

### 2.1. Các chỉ số vàng cần giám sát (The 4 Golden Signals)
Hệ thống xuất bản các chỉ số qua giao thức Prometheus tại `/actuator/prometheus` (Backend) và `/minio/v2/metrics/cluster` (Storage):

| Nhóm chỉ số | Metric Prometheus | Ngưỡng bình thường | Ngưỡng Cảnh báo (Alert) |
| :--- | :--- | :---: | :---: |
| **1. Độ trễ (Latency)** | `http_server_requests_seconds{quantile="0.95"}` | p95 < 200 ms | p95 > 2.000 ms (2s) |
| **2. Lưu lượng (Traffic)** | `rate(http_server_requests_seconds_count[5m])` | 10 - 200 req/s | > 1.000 req/s (Nghi vấn DDoS) |
| **3. Tỷ lệ lỗi (Errors)** | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | 0% | > 1% tổng lưu lượng |
| **4. Bão hòa (Saturation)** | `hikaricp_connections_active` / `hikaricp_connections_max` | < 40% (2 - 8 conn) | > 85% (> 20 conn) |
| **5. Bộ nhớ Heap JVM** | `jvm_memory_used_bytes{area="heap"}` | 120 MB - 350 MB | > 1.600 MB (80% Limit) |

### 2.2. Kiểm tra nhanh số liệu giám sát từ dòng lệnh
```bash
# 1. Đo lường tỷ lệ kết nối CSDL HikariCP đang hoạt động
curl -s http://localhost:8089/actuator/prometheus | grep "hikaricp_connections"

# 2. Đo lường tỷ lệ rác được thu gom (Garbage Collection ZGC)
curl -s http://localhost:8089/actuator/prometheus | grep "jvm_gc_pause_seconds_count"

# 3. Kiểm tra số lượng phiên người dùng đang hoạt động
curl -s http://localhost:8089/actuator/metrics/http.server.requests
```

---

## 3. QUẢN TRỊ NHẬT KÝ VÀ TRUY VẾT KIỂM TOÁN (LOGGING & AUDIT TRAIL)

### 3.1. Cấu trúc nhật ký JSON có cấu trúc (Structured JSON Logging)
Mọi bản ghi nhật ký ứng dụng Backend và Nginx đều được chuẩn hóa theo định dạng JSON chứa timestamp, client IP, requestId, userRole, URL và thời gian xử lý:

```bash
# Xem trực tiếp nhật ký ứng dụng Backend thời gian thực
docker compose -f docker-compose.prod.yml logs -f --tail=100 backend

# Lọc các dòng nhật ký có mức độ WARN hoặc ERROR
docker compose -f docker-compose.prod.yml logs --tail=500 backend | grep -E '"level":"(WARN|ERROR)"'
```

### 3.2. Truy vết kiểm toán thay đổi dữ liệu (Audit Log Inspection)
Theo Thông tư 06/2023/TT-BTTTT, toàn bộ các thao tác Create, Update, Delete và Import dữ liệu hạ tầng đều được ghi vào bảng `audit_log` với địa chỉ IP, vai trò và thông tin trước/sau sửa đổi:

```bash
# Truy vấn 10 thao tác quản trị gần nhất từ cán bộ
docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
SELECT id, action_type, entity_name, entity_id, actor_username, actor_role, client_ip, created_at
FROM audit_log
ORDER BY id DESC LIMIT 10;"
```

### 3.3. Xoay vòng nhật ký tự động (Log Rotation)
Dịch vụ `logrotate` được cấu hình tại [`deploy/monitoring/logrotate.conf`](file:///c:/Demo_CucDuongBo/deploy/monitoring/logrotate.conf), tự động chạy hàng ngày:
- Nén tệp log thành `.gz` sau 24 giờ.
- Lưu trữ lịch sử nhật ký trong vòng **14 ngày**.
- Tự động xóa các tệp log cũ quá 14 ngày để bảo vệ dung lượng đĩa.

---

## 4. QUY TRÌNH BẢO TRÌ CƠ SỞ DỮ LIỆU ĐỊNH KỲ (DATABASE MAINTENANCE)

### 4.1. Lập chỉ mục và Tối ưu hóa thống kê (Hàng tuần vào 02:00 sáng Chủ Nhật)
Sau các đợt cập nhật hoặc nhập thêm dữ liệu đường bộ lớn:

```bash
# 1. Cập nhật phân phối xác suất và lược đồ thống kê cho bộ lập kế hoạch truy vấn
docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
VACUUM (ANALYZE, VERBOSE) raw_dataset_record;
VACUUM (ANALYZE, VERBOSE) dataset_registry;
VACUUM (ANALYZE, VERBOSE) audit_log;
"

# 2. Tái tạo chỉ mục chống phân mảnh B-Tree (Không khóa bảng)
docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
REINDEX TABLE CONCURRENTLY raw_dataset_record;
"
```

### 4.2. Làm mới Materialized View Tổng hợp Báo cáo Dashboard
Materialized view `mv_dashboard_branch_stats` lưu trữ số liệu tổng hợp của 4 Khu Quản lý Đường bộ. Cần làm mới sau mỗi đợt nạp dữ liệu:

```bash
docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_dashboard_branch_stats;
"
```

### 4.3. Phát hiện và xử lý Truy vấn chậm (Slow Queries Tracking)
Tham số `log_min_duration_statement = 2000` tự động ghi lại mọi truy vấn vượt quá 2 giây vào nhật ký PostgreSQL:

```bash
# Xem các truy vấn chậm trong container CSDL
docker logs --tail=500 kcht_prod_postgres | grep "duration:"
```

---

## 5. BẢO TRÌ VÙNG LƯU TRỮ TỆP TIN MINIO / S3 (OBJECT STORAGE)

### 5.1. Kiểm tra dung lượng và số lượng đối tượng các Bucket
```bash
docker exec kcht_prod_minio_init /usr/bin/mc du myminio/kcht-documents
docker exec kcht_prod_minio_init /usr/bin/mc du myminio/kcht-media
docker exec kcht_prod_minio_init /usr/bin/mc du myminio/kcht-quarantine
```

### 5.2. Quản lý tệp trong Vùng cách ly (Quarantine Management)
Các tệp tải lên bị nghi vấn chứa mã độc hoặc sai định dạng MIME sẽ được chuyển tự động vào bucket `kcht-quarantine`. Kỹ sư định kỳ kiểm tra và xóa tệp rác:

```bash
# Liệt kê các tệp bị cách ly
docker exec kcht_prod_minio_init /usr/bin/mc ls myminio/kcht-quarantine

# Xóa các tệp cách ly cũ quá 30 ngày
docker exec kcht_prod_minio_init /usr/bin/mc rm --recursive --force myminio/kcht-quarantine/
```

---

## 6. KỊCH BẢN ỨNG PHÓ VÀ XỬ LÝ SỰ CỐ KHẨN CẤP (INCIDENT RESPONSE PLAYBOOKS)

### Kịch bản 1: Cạn kiệt kết nối Cơ sở dữ liệu (Database Connection Pool Starvation)
- **Dấu hiệu**: Ứng dụng phản hồi chậm, log xuất hiện lỗi `HikariPool - Connection is not available, request timed out after 20000ms`.
- **Hành động xử lý**:
  1. Kiểm tra các câu truy vấn bị nghẽn (Idle in transaction):
     ```bash
     docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
     SELECT pid, usename, client_addr, state, query_start, query 
     FROM pg_stat_activity 
     WHERE state != 'idle' ORDER BY query_start ASC LIMIT 5;"
     ```
  2. Hủy các truy vấn chạy quá 60 giây gây khóa dòng:
     ```bash
     docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
     SELECT pg_cancel_backend(pid) FROM pg_stat_activity WHERE state != 'idle' AND query_start < NOW() - INTERVAL '60 seconds';"
     ```

### Kịch bản 2: Bộ nhớ RAM máy chủ vượt ngưỡng 90% (Out-Of-Memory Risk)
- **Dấu hiệu**: Hệ thống có nguy cơ bị Linux Kernel OOM Killer tiêu diệt tiến trình.
- **Hành động xử lý**:
  1. Kiểm tra tiến trình chiếm dụng RAM: `docker stats --no-stream`.
  2. Khởi động lại container Backend có kiểm soát (nhờ cờ `-XX:+ExitOnOutOfMemoryError` và ZGC, JVM sẽ tự khởi động lại):
     ```bash
     docker compose -f docker-compose.prod.yml restart backend
     ```
  3. Xóa bộ nhớ đệm cache hệ điều hành: `sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches`.

### Kịch bản 3: Dung lượng Ổ cứng vượt ngưỡng 85% (Disk Full Alert)
- **Hành động xử lý**:
  1. Dọn dẹp Docker container và images không sử dụng:
     ```bash
     docker system prune -af --volumes=false
     ```
  2. Nén và chuyển các bản sao lưu cũ ra ổ cứng ngoài hoặc S3 Glacier:
     ```bash
     find ./deploy/backups/postgres -type f -name "*.dump" -mtime +14 -exec gzip {} \;
     ```

### Kịch bản 4: Chứng chỉ số TLS/SSL hết hạn (TLS Certificate Expiration)
- **Hành động xử lý**:
  ```bash
  # 1. Gia hạn tự động qua Certbot
  sudo certbot renew --force-renewal
  
  # 2. Cập nhật khóa mới vào thư mục Nginx
  sudo cp /etc/letsencrypt/live/kcht.drvn.gov.vn/fullchain.pem ./deploy/nginx/ssl/kcht_tls.crt
  sudo cp /etc/letsencrypt/live/kcht.drvn.gov.vn/privkey.pem ./deploy/nginx/ssl/kcht_tls.key
  
  # 3. Reload Nginx không gián đoạn
  docker exec kcht_prod_nginx nginx -s reload
  ```
