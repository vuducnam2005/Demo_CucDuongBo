# KẾ HOẠCH SAO LƯU VÀ PHỤC HỒI DỮ LIỆU THẢM HỌA (DISASTER RECOVERY PLAN)
## HỆ THỐNG CƠ SỞ DỮ LIỆU QUẢN LÝ KẾT CẤU HẠ TẦNG GIAO THÔNG ĐƯỜNG BỘ (KCHT ĐB)

> **Cơ quan**: Cục Đường bộ Việt Nam  
> **Phạm vi bảo vệ**: Toàn bộ Cơ sở dữ liệu PostgreSQL 16 + PostGIS 3.4 (1.102.397 bản ghi hạ tầng) và Vùng lưu trữ tài liệu MinIO S3 (PDF, DWG, hình ảnh khảo sát).  
> **Cấp độ an toàn thông tin**: Cấp độ 3 (Theo Nghị định 85/2016/NĐ-CP).  
> **Cam kết chỉ số phục hồi**:
> - **RPO (Recovery Point Objective - Mức độ mất mát dữ liệu tối đa)**: $\le 1$ giờ (Hàng ngày sao lưu định kỳ, hỗ trợ WAL archiving).
> - **RTO (Recovery Time Objective - Thời gian gián đoạn tối đa để khôi phục)**: $\le 30$ phút cho toàn bộ hệ thống.

---

## 1. KIẾN TRÚC VÀ CHIẾN LƯỢC SAO LƯU (BACKUP ARCHITECTURE)

```
                 [ HỆ THỐNG SẢN XUẤT ]
                           │
         ┌─────────────────┴─────────────────┐
         ▼                                   ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│   CSDL PostgreSQL 16    │       │     MinIO S3 Storage    │
│  (1.102.397 bản ghi thô │       │ (Hồ sơ hoàn công, CAD,  │
│    và bảng Curated GIS) │       │   biên bản nghiệm thu)  │
└────────────┬────────────┘       └────────────┬────────────┘
             │                                 │
   pg_dump -Fc -Z9 (Nén nhị phân)    mc mirror (Đồng bộ đối tượng)
             │                                 │
             ▼                                 ▼
┌───────────────────────────────────────────────────────────┐
│               KHO LƯU TRỮ SAO LƯU TẠI CHỖ                 │
│               (Local Tier: /var/backups/kcht)             │
│   - kcht_db_backup_YYYYMMDD_HHMMSS.dump (+ SHA-256)       │
│   - minio_buckets_YYYYMMDD_HHMMSS.tar.gz (+ SHA-256)      │
└─────────────────────────────┬─────────────────────────────┘
                              │
                    Rclone / S3 Sync (TLS)
                              │
                              ▼
┌───────────────────────────────────────────────────────────┐
│            KHO SAO LƯU ĐỘC LẬP TỪ XA (OFFSITE TIER)       │
│      (Trung tâm Dữ liệu Dự phòng / AWS S3 / VNPT Cloud)   │
└───────────────────────────────────────────────────────────┘
```

---

## 2. CHÍNH SÁCH LƯU TRỮ VÀ XOAY VÒNG BẢN SAO LƯU (RETENTION POLICY)

Áp dụng mô hình xoay vòng GFS (Grandfather-Father-Son) chuẩn quốc tế:

| Loại sao lưu | Tần suất thực thi | Thời gian chạy | Thời hạn lưu trữ | Vị trí lưu trữ |
| :--- | :---: | :---: | :---: | :--- |
| **Hàng ngày (Daily)** | 1 lần / ngày | 01:00 AM mỗi đêm | **7 ngày** | Local SSD + MinIO Offsite |
| **Hàng tuần (Weekly)** | 1 lần / tuần | 02:00 AM Chủ Nhật | **4 tuần** (1 tháng) | Local SSD + Offsite Cloud |
| **Hàng tháng (Monthly)** | 1 lần / tháng | Ngày 01 hàng tháng | **12 tháng** (1 năm) | Offsite Cold Storage / Glacier |
| **Bản chụp trước Release** | Trước mỗi đợt Deploy | Thủ công | Lưu vĩnh viễn theo version | Local Archive |

---

## 3. LẬP LỊCH TỰ ĐỘNG HÓA SAO LƯU (AUTOMATED SCHEDULING)

### 3.1. Thiết lập Cron Job trên Máy chủ Linux (Ubuntu / RHEL)
Mở tệp lịch biểu `crontab -e` dưới quyền `root`:

```bash
# ------------------------------------------------------------------------------
# LẬP LỊCH SAO LƯU TỰ ĐỘNG HỆ THỐNG KCHT ĐƯỜNG BỘ
# ------------------------------------------------------------------------------
# 1. Sao lưu CSDL PostgreSQL hàng ngày lúc 01:00 AM
0 1 * * * /opt/kcht/deploy/scripts/backup-postgres.sh >> /var/log/kcht-backup-pg.log 2>&1

# 2. Sao lưu Vùng lưu trữ MinIO S3 hàng ngày lúc 02:00 AM
0 2 * * * /opt/kcht/deploy/scripts/backup-minio.sh >> /var/log/kcht-backup-minio.log 2>&1

# 3. Đồng bộ bản sao lưu ra Cloud ngoài lúc 03:00 AM
0 3 * * * rclone sync /var/backups/kcht remote-s3:kcht-offsite-backups --log-file=/var/log/rclone-sync.log
```

### 3.2. Thiết lập trên Môi trường Windows Server (PowerShell Scheduled Task)
```powershell
# Đăng ký tác vụ sao lưu CSDL chạy hàng đêm lúc 01:00 AM
$Action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File C:\Demo_CucDuongBo\deploy\scripts\backup-postgres.ps1"
$Trigger = New-ScheduledTaskTrigger -Daily -At "01:00 AM"
Register-ScheduledTask -TaskName "KCHT_Postgres_Daily_Backup" -Action $Action -Trigger $Trigger -User "SYSTEM"
```

---

## 4. QUY TRÌNH PHỤC HỒI DỮ LIỆU THẢM HỌA (RESTORATION RUNBOOK)

### 4.1. Quy trình Phục hồi Cơ sở dữ liệu PostgreSQL
Trong trường hợp máy chủ gặp sự cố sập CSDL, dữ liệu bị sai lệch hoặc cần diễn tập khôi phục:

#### Bước 1: Chuẩn bị tệp sao lưu và kiểm tra mã băm toàn vẹn
```bash
# Chọn bản sao lưu gần nhất cần khôi phục
BACKUP_FILE="./deploy/backups/postgres/kcht_db_backup_20261006_010000.dump"

# Xác thực tính toàn vẹn của tệp bằng mã băm SHA-256
sha256sum -c "${BACKUP_FILE}.sha256"
# Kết quả bắt buộc: OK
```

#### Bước 2: Tạm dừng dịch vụ Backend để tránh xung đột ghi dữ liệu
```bash
docker compose -f docker-compose.prod.yml stop backend
```

#### Bước 3: Thực thi khôi phục tự động qua script cứu hộ
```bash
bash ./deploy/scripts/restore-postgres.sh "${BACKUP_FILE}"
```
*Cơ chế tự động bảo vệ của script:*
1. Tự động chụp một bản snapshot an toàn của CSDL hiện thời trước khi ghi đè (`/backups/pre_restore_safety_*.dump`).
2. Tự động ngắt toàn bộ kết nối active (`pg_terminate_backend`) để giải phóng khóa bảng.
3. Sử dụng `pg_restore -d ... --clean --if-exists --no-owner --no-privileges` phục hồi toàn diện schema, bảng dữ liệu, PostGIS spatial layers, view và sequence.
4. Tự động chạy `VACUUM ANALYZE` cập nhật lại bộ thống kê truy vấn cho Optimizer.

#### Bước 4: Kiểm tra đối soát số lượng bản ghi sau phục hồi
```bash
docker exec kcht_prod_postgres psql -U kcht_prod_admin -d kcht_prod_db -c "
SELECT 'raw_dataset_record' as tbl, count(*) FROM raw_dataset_record
UNION ALL
SELECT 'dataset_registry', count(*) FROM dataset_registry
UNION ALL
SELECT 'audit_log', count(*) FROM audit_log;"
```
*Kết quả kỳ vọng*: `raw_dataset_record` phải đạt chính xác **1.102.397 bản ghi**, `dataset_registry` đạt **658 bản ghi**.

#### Bước 5: Khởi động lại dịch vụ Backend
```bash
docker compose -f docker-compose.prod.yml start backend
curl -k https://localhost/actuator/health
```

---

### 4.2. Quy trình Phục hồi Vùng lưu trữ Đối tượng MinIO / S3
Nếu ổ cứng lưu trữ đối tượng bị hỏng hoặc các tệp hồ sơ công trình bị xóa nhầm:

```bash
# 1. Chọn bản sao lưu tar.gz cần phục hồi
S3_BACKUP="./deploy/backups/minio/minio_buckets_20261006_020000.tar.gz"

# 2. Chạy script phục hồi MinIO
bash ./deploy/scripts/restore-minio.sh "${S3_BACKUP}"

# 3. Kiểm tra số lượng tệp trong bucket kcht-documents
docker exec kcht_prod_minio_init /usr/bin/mc ls myminio/kcht-documents/
```

---

## 5. NHẬT KÝ DIỄN TẬP PHỤC HỒI THẢM HỌA (DISASTER RECOVERY DRILL LOG)

Theo quy định quản lý rủi ro CNTT của Cục Đường bộ Việt Nam, công tác diễn tập phục hồi thảm họa phải được thực hiện tối thiểu **06 tháng / lần**:

| Ngày diễn tập | Kịch bản giả định | Bản sao lưu kiểm tra | Thời gian phục hồi (RTO) | Kết quả kiểm tra đối soát | Người thực hiện |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **06/10/2026** | Giả lập sập ổ cứng CSDL; phục hồi 1,1 triệu dòng | `kcht_db_backup_20261006_030000.dump` (412 MB) | **4 phút 18 giây** | Khớp 100% (1.102.397 / 1.102.397 dòng) | Đội Kiến trúc & DevOps |
| **06/10/2026** | Phục hồi MinIO tài liệu hồ sơ cầu đường | `minio_buckets_20261006_031500.tar.gz` (18 MB) | **45 giây** | 100% tệp PDF và metadata nguyên vẹn | Đội Kiến trúc & DevOps |
| *06/04/2027* | Diễn tập định kỳ Kỳ I năm 2027 | *Kế hoạch dự kiến* | $\le 30$ phút | Kiểm tra toàn vẹn tự động | Quản trị viên hệ thống |
