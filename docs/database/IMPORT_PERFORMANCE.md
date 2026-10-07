# BÁO CÁO ĐO LƯỜNG HIỆU NĂNG VÀ TỐI ƯU HÓA IMPORT
*(IMPORT_PERFORMANCE.md)*

**Dự án:** Cơ sở dữ liệu Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT)  
**Thời điểm thực hiện:** 2026-10-05  
**Hạ tầng kiểm chuẩn:**
- **CPU:** 16 Cores AMD Ryzen
- **RAM Máy Host:** 32 GB
- **Database:** PostgreSQL 16 + PostGIS 3.4 (Docker Alpine, giới hạn volume bền vững)
- **Runtime:** Java 21 LTS, Spring Boot 3.3.4, HikariCP, Jackson Streaming API
- **JVM Heap Cấu hình:** `-Xms256m -Xmx1024m`

---

## 1. TỔNG QUAN KẾT QUẢ ĐO LƯỜNG

Hệ thống đã trải qua các bài kiểm chuẩn tải thực tế trên các tập dữ liệu đại diện từ nhỏ (169 dòng) đến rất lớn (222.112 dòng, 789 MB).

### 1.1. Bảng tổng hợp các kịch bản nạp

| Kịch bản kiểm thử | Tập dữ liệu | Kích thước | Số bản ghi | Thời gian (ms) | Tốc độ (rec/s) | RAM Heap sử dụng |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Dry-Run (Stream & Validate)** | 3 datasets gộp | 917 MB | 233.912 | 7.951 ms | **29.419 rec/s** | 95 MB |
| **Fresh Ingest (Ghi mới hoàn toàn)** | `mst_national_road` | 478 KB | 169 | 254 ms | 665 rec/s | 68 MB |
| **Fresh Ingest (Ghi mới + GIS)** | `tbl_bridge` | 127 MB | 11.631 | 8.075 ms | 1.440 rec/s | 85 MB |
| **Fresh Ingest (Tải lớn nhất)** | `tbl_road_sign` | 789 MB | 222.112 | 83.479 ms | **2.660 rec/s** | 110 MB |
| **Idempotency Skip (Quét lại toàn bộ)** | `tbl_road_sign` | 789 MB | 222.112 | 14.052 ms | **15.806 rec/s** | 105 MB |
| **Idempotency Skip (3 datasets)** | 3 datasets gộp | 917 MB | 233.912 | 12.144 ms | **19.261 rec/s** | 108 MB |

---

## 2. PHÂN TÍCH VÀ ĐO ĐẠC TRANSACTION BATCH SIZE

Để xác định kích thước lô giao dịch (`batch_size`) tối ưu, tiến hành thử nghiệm trên tập dữ liệu `assets/tbl_bridge.json` (11.631 bản ghi) với 4 mức `batch_size`: **250**, **500**, **1.000**, và **2.000**.

### 2.1. Kết quả so sánh thực tế

```
+------------+---------------+----------------+----------------+----------------+
| Batch Size | Thời gian (ms)| Tốc độ (rec/s) | RAM Heap (MB)  | Số Transactions|
+------------+---------------+----------------+----------------+----------------+
|    250     |    2.251 ms   |  5.167 rec/s   |     72 MB      |     47 tx      |
|    500     |    2.209 ms   |  5.265 rec/s   |     85 MB      |     24 tx      |  <-- TỐI ƯU NHẤT
|   1.000    |    3.128 ms   |  3.718 rec/s   |    110 MB      |     12 tx      |
|   2.000    |    2.975 ms   |  3.909 rec/s   |    227 MB      |      6 tx      |
+------------+---------------+----------------+----------------+----------------+
```

### 2.2. Nhận xét kỹ thuật
1. **Batch Size = 500 là điểm cân bằng lý tưởng (Sweet Spot):**
   - Đạt tốc độ cao nhất (~5.265 bản ghi/giây).
   - Mức tiêu thụ bộ nhớ RAM rất thấp và ổn định (85 MB).
   - Thời gian giữ khóa giao dịch (Transaction Holding Time) ngắn, giúp giảm thiểu tranh chấp hàng rào khóa (Lock Contention) trên PostgreSQL.
2. **Khi Batch Size >= 1.000:**
   - Câu lệnh `IN (?, ?, ...)` phải chứa 1.000 đến 2.000 tham số, làm tăng thời gian phân tích cú pháp SQL (SQL Parsing) của PostgreSQL.
   - Danh sách đối tượng lưu tạm trong bộ nhớ JVM tăng vọt (ở batch 2.000, RAM nhảy lên 227 MB).
3. **Khi Batch Size <= 250:**
   - Số lượng transaction tăng gấp đôi, chi phí commit round-trip qua kết nối mạng/socket tăng nhẹ.

> **Khuyến nghị chính thức:** Đặt tham số cấu hình mặc định:
> ```yaml
> kcht:
>   import:
>     batch-size: 500
> ```

---

## 3. ĐO LƯỜNG TIÊU THỤ BỘ NHỚ RAM VÀ JVM PROFILE

### 3.1. Cơ chế Streaming bảo vệ RAM
- Hệ thống áp dụng **Jackson Streaming API (`JsonParser` kết hợp `nextToken`)**, đọc từng token JSON theo luồng từ ổ đĩa qua buffer 64 KB.
- Tuyệt đối không nạp cả file vào cấu trúc cây DOM hay `JsonNode` toàn phần trên RAM.

### 3.2. Mức tiêu thụ thực tế
- Khi xử lý tệp lớn nhất hệ thống `assets/tbl_road_sign.json` (**789.775.158 bytes ~ 789 MB**):
  - **RAM Khởi động:** ~60 MB
  - **RAM Đỉnh điểm trong lúc stream và commit:** **105 MB - 110 MB**
  - **Tỷ lệ bộ nhớ:** Chiếm chưa đầy **11%** dung lượng của file dữ liệu nguồn!
- Ngay sau khi hoàn tất nạp tệp, Garbage Collector (G1GC) thu hồi về ngưỡng ~40 MB.
- Điều này chứng minh ứng dụng có thể chạy hoàn toàn ổn định trên container giới hạn RAM chỉ **512 MB - 1 GB** mà không bao giờ gặp nguy cơ tràn bộ nhớ (`OutOfMemoryError`).

---

## 4. PHÂN TÍCH CHỈ MỤC VÀ KHÓA (LOCKS & INDEXES)

### 4.1. Phân bổ dung lượng bảng `raw_dataset_record` (233.912 bản ghi)

Truy vấn đối soát dung lượng vật lý trên đĩa:
```sql
SELECT 
    pg_size_pretty(pg_table_size('raw_dataset_record')) as data_size,
    pg_size_pretty(pg_indexes_size('raw_dataset_record')) as indexes_size,
    pg_size_pretty(pg_total_relation_size('raw_dataset_record')) as total_size;
```

**Kết quả:**
- **Dữ liệu thô (Data Size):** **440 MB**
- **Tổng dung lượng chỉ mục (Indexes Size):** **136 MB**
- **Tổng dung lượng bảng (Total Relation Size):** **576 MB**

**Chi tiết kích thước từng chỉ mục:**
| Tên chỉ mục | Kiểu chỉ mục | Cột tham gia | Kích thước | Mục đích nghiệp vụ |
| :--- | :---: | :--- | :---: | :--- |
| `idx_raw_payload_gin` | **GIN** | `raw_payload (jsonb_path_ops)` | **67 MB** | Tìm kiếm jsonb nhanh theo cặp key-value |
| `idx_raw_payload_sha256` | **B-Tree** | `payload_sha256` | **28 MB** | Đối soát chống trùng lặp payload |
| `uq_raw_dataset_record` | **B-Tree (Unique)**| `(dataset_key, record_key)` | **16 MB** | Ràng buộc duy nhất, chống duplicate |
| `idx_raw_record_key` | **B-Tree** | `record_key` | **12 MB** | Tra cứu nhanh theo mã tài sản |
| `raw_dataset_record_pkey`| **B-Tree (PK)** | `id` | **5.1 MB** | Khóa chính tự tăng |
| `idx_raw_imported_at` | **B-Tree** | `imported_at DESC` | **3.8 MB** | Sắp xếp lịch sử nạp mới nhất |
| `idx_raw_dataset_status`| **B-Tree** | `(dataset_key, record_status)`| **2.1 MB** | Lọc tiến trình curation |
| `idx_raw_job_id` | **B-Tree** | `import_job_id` | **1.8 MB** | Liên kết phiên nạp |

*Nhận xét:* Tỷ lệ chỉ mục / dữ liệu là **136 MB / 440 MB (~31%)**, đây là tỷ lệ rất lành mạnh cho bảng CSDL vận hành có hỗ trợ GIN index.

### 4.2. Phân tích Khóa (Lock Analysis) và Cảnh báo Song song
- Khi thực thi lệnh `INSERT INTO raw_dataset_record ...`:
  - PostgreSQL thiết lập khóa **`RowExclusiveLock`** trên bảng `raw_dataset_record`.
  - Trong ma trận khóa PostgreSQL, `RowExclusiveLock` **tương thích (compatible)** với chính nó! Tức là nhiều worker có thể cùng ghi vào bảng `raw_dataset_record` cùng lúc.
- **RỦI RO TIỀM ẨN:**
  - Nếu 2 worker cùng xử lý các bản ghi của **cùng một file / cùng tập `record_key`**:
    - Khi Worker A và Worker B cùng chạm vào cùng một dòng trên index duy nhất `uq_raw_dataset_record`, một worker sẽ bị rơi vào trạng thái chờ hàng đợi khóa hàng (`ShareLock` trên tuple). Nếu vòng lặp xảy ra, có thể phát sinh `DeadlockException`.
- **QUY TẮC AN TOÀN SONG SONG (CONCURRENCY SAFETY RULE):**
  > **Chỉ thực hiện song song hóa theo đơn vị TẬP DỮ LIỆU (Per-Dataset / Per-File).**  
  > Mỗi Worker chỉ xử lý 1 file riêng biệt. Tuyệt đối không chia nhỏ một file cho nhiều worker ghi đồng thời.

---

## 5. ĐỀ XUẤT CẤU HÌNH CHO TOÀN BỘ BỘ DỮ LIỆU (3+ GB, 658 DATASETS)

Dựa trên toàn bộ kết quả đo kiểm, cấu hình tối ưu khuyến nghị cho giai đoạn import toàn bộ:

```yaml
kcht:
  import:
    # 1. Kích thước lô giao dịch tối ưu: 500 bản ghi/lô
    batch-size: 500
    
    # 2. Số lượng luồng worker chạy song song (Parallel Files): 4 worker
    # Lý do: Mỗi worker xử lý 1 dataset độc lập, không xung đột lock uq_raw_dataset_record
    workers: 4

spring:
  datasource:
    hikari:
      # 3. Kích thước connection pool: 8 - 10 connections
      maximum-pool-size: 10
      minimum-idle: 4
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 500
          order_inserts: true
```

### 5.1. Ước tính thời gian và tài nguyên khi nạp toàn bộ 658 datasets:
- **Tổng số bản ghi ước tính:** ~1.100.000 bản ghi.
- **Tổng dung lượng JSON thô:** ~3,86 GB.
- **Thời gian chạy với 1 worker tuần tự:** ~7 - 9 phút.
- **Thời gian chạy với 4 worker song song:** **~2,5 - 3,5 phút**.
- **RAM JVM cần cấp phát:** `-Xms512m -Xmx2048m`.
- **Dung lượng CSDL dự kiến sau khi nạp toàn bộ:** **~2,4 - 2,8 GB** (bao gồm dữ liệu JSONB và đầy đủ 8 chỉ mục).
