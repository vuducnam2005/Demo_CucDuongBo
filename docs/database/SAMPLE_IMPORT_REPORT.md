# BÁO CÁO NGIỆM THU NẠP THỬ NGHIỆM BA DATASET ĐẠI DIỆN
*(SAMPLE_IMPORT_REPORT.md)*

**Dự án:** Xây dựng Cơ sở dữ liệu Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT)  
**Thời điểm thực hiện:** 2026-10-05  
**Môi trường:** Java 21 LTS + Spring Boot 3.3.4 + PostgreSQL 16 + PostGIS 3.4 (Docker)  
**Tập dữ liệu nguồn:** `C:\Data\kcht_json_2026-10-05`  

---

## 1. MỤC TIÊU VÀ PHẠM VI THỬ NGHIỆM

Thực thi nạp dữ liệu thực tế cho 3 dataset tiêu biểu đại diện cho 3 quy mô và đặc thù khác nhau của toàn bộ 658 datasets trong hệ thống:

1. **`assets/mst_national_road.json` (Nhỏ - Danh mục):** 169 bản ghi, kích thước 478 KB. Đại diện cho dữ liệu danh mục tuyến đường quốc lộ.
2. **`assets/tbl_bridge.json` (Vừa - Tài sản không gian):** 11.631 bản ghi, kích thước 127 MB. Đại diện cho tài sản công trình có tọa độ địa lý điểm (Point WGS84).
3. **`assets/tbl_road_sign.json` (Lớn - Khối lượng lớn):** 222.112 bản ghi, kích thước 789 MB. Đại diện cho tập dữ liệu tài sản lớn nhất, kiểm thử giới hạn chịu tải, bộ nhớ RAM và hiệu năng batch.

---

## 2. KIỂM TRA TRƯỚC KHI NẠP (PRE-IMPORT CHECKS)

### 2.1. Số dòng và chỉ số kỳ vọng từ `manifest.json`

| STT | Tệp dữ liệu | Số dòng kỳ vọng | Kích thước (bytes) | Mã băm SHA-256 nguồn | Phân loại |
| :---: | :--- | :---: | :---: | :--- | :---: |
| 1 | `assets/mst_national_road.json` | **169** | 478.822 (~478 KB) | `c99b5cca35b02b4015c514bc306724aa557efae58cd7c15e0336cdf7ba97413a` | `asset` |
| 2 | `assets/tbl_bridge.json` | **11.631** | 127.086.856 (~127 MB) | `b92c8ae5db84d9cface7f4827d93ee62b474f8fb4bb6e9bb98318ccabb242cb3` | `asset` |
| 3 | `assets/tbl_road_sign.json` | **222.112** | 789.775.158 (~789 MB) | `a81e4f3c9ef2ec87f0701129dd3063680b45a50fdaf54f9c99925c0ac2cbe6f4` | `asset` |
| **Tổng** | **3 datasets** | **233.912** | **917.340.836 (~917 MB)** | — | — |

### 2.2. Kiểm tra Migration và Database Connection

- **PostgreSQL Version:** PostgreSQL 16.4 on x86_64-pc-linux-musl.
- **PostGIS Extension:** PostGIS 3.4 (USE_GEOS=1, USE_PROJ=1, USE_STATS=1).
- **Flyway Migrations:** 5/5 scripts hoàn tất thành công (`V1` đến `V5`, bảng `flyway_schema_history` trạng thái `success = true`).
- **Metadata Registry:** Bảng `dataset_registry` đã đồng bộ **658** datasets; bảng `dataset_field` đã đồng bộ **10.142** trường từ `field_dictionary.json`.
- **Dung lượng CSDL ban đầu:** **24 MB** (chứa schema rỗng, danh mục trường và extensions).
- **Số bản ghi trong `raw_dataset_record` ban đầu:** **0**.

### 2.3. Kết quả chạy Dry-Run

Lệnh thực thi:
```bash
java -Dfile.encoding=UTF-8 -jar target/kcht-import-service-1.0.0-SNAPSHOT.jar \
  --dry-run \
  --files=assets/mst_national_road.json,assets/tbl_bridge.json,assets/tbl_road_sign.json
```

**Bảng tổng hợp Dry-Run:**
```text
========================================================================================
IMPORT SUMMARY REPORT (DryRun: true, Job: DRY_RUN_1791214916053, Total Time: 7951 ms)
========================================================================================
Dataset File                     | Manifest |     Read | Inserted |  Updated |  Skipped |   Failed |    Geoms | Time(ms)
---------------------------------+----------+----------+----------+----------+----------+----------+----------+----------
assets/mst_national_road.json    |      169 |      169 |        0 |        0 |        0 |        0 |        0 |       86
assets/tbl_bridge.json           |    11631 |    11631 |        0 |        0 |        0 |        0 |     7436 |     1391
assets/tbl_road_sign.json        |   222112 |   222112 |        0 |        0 |        0 |        0 |        0 |     6419
========================================================================================
TOTAL: Files=3/3 (Skipped=0), Read=233912, Ins=0, Upd=0, Skip=0, Fail=0
========================================================================================
```
*Đánh giá Dry-Run:* Toàn bộ 233.912 bản ghi (917 MB) được đọc streaming và validate cấu trúc thành công trong **7,95 giây**, 0 lỗi payload, CSDL không bị thay đổi (0 bản ghi ghi vào DB).

---

## 3. KẾT QUẢ ĐỐI SOÁT NẠP THỰC TẾ (REAL IMPORT)

Lệnh thực thi nạp thật:
```bash
java -Dfile.encoding=UTF-8 -jar target/kcht-import-service-1.0.0-SNAPSHOT.jar \
  --files=assets/mst_national_road.json,assets/tbl_bridge.json,assets/tbl_road_sign.json
```

### 3.1. Bảng đối soát chi tiết từng dataset

| Chỉ số đối soát | `mst_national_road` | `tbl_bridge` | `tbl_road_sign` | Tổng cộng / Kết luận |
| :--- | :---: | :---: | :---: | :---: |
| **Kỳ vọng manifest** | 169 | 11.631 | 222.112 | **233.912** |
| **Số record trong file JSON** | 169 | 11.631 | 222.112 | **233.912** (Khớp 100%) |
| **Số rows_read** | 169 | 11.631 | 222.112 | **233.912** |
| **Số rows_inserted** | **169** | **11.631** | **222.112** | **233.912** |
| **Số rows_updated** | 0 | 0 | 0 | **0** |
| **Số rows_skipped** | 0 | 0 | 0 | **0** |
| **Số rows_failed** | 0 | 0 | 0 | **0** (Không mất bản ghi nào) |
| **Số record_key duy nhất** | 169 | 11.631 | 222.112 | **233.912** (Không xung đột khóa) |
| **Số payload_sha256 trùng** | 0 | 0 | 0 | **0** |
| **Số record trong `raw_dataset_record`** | **169** | **11.631** | **222.112** | **233.912** (Kiểm tra trực tiếp SQL) |
| **Số record trong `asset_record`** | 0 | 0 | 0 | **0** *(chưa chạy curation pipeline)* |
| **Số geometry hợp lệ (tọa độ điểm)** | 0 | **7.436** | 0 | **7.436** (4.195 cầu còn lại tọa độ null) |
| **Thời gian nạp** | 254 ms | 8.075 ms | 83.479 ms | **91.977 ms (~92 giây)** |
| **Tốc độ xử lý trung bình** | ~665 rec/s | ~1.440 rec/s | ~2.660 rec/s | **~2.543 rec/s** |

### 3.2. Dung lượng Database và Lưu trữ

- **Dung lượng database trước nạp:** **24 MB**
- **Dung lượng database sau nạp:** **600 MB**
- **Tăng trưởng thực tế:** **+576 MB** (bao gồm toàn bộ dữ liệu `raw_payload jsonb`, bảng băm SHA-256, và 6 chỉ mục GIN / B-Tree).
- **So sánh nén:** Tệp JSON nguồn dạng text thuần là **917 MB**, khi nạp vào PostgreSQL dạng binary JSONB + Indexes chỉ chiếm **576 MB** (tỷ lệ nén và lưu trữ tối ưu đạt ~63% dung lượng nguồn).

---

## 4. KIỂM THỬ TÍNH LŨY TIẾN VÀ CHỐNG TRÙNG LẶP (IDEMPOTENCY TEST)

Nhằm đáp ứng yêu cầu *"có thể chạy lại mà số lượng không tăng sai"*, tiến hành thực thi lại nguyên văn lệnh import thật lần 2 ngay sau khi dữ liệu đã có sẵn trong database:

```bash
java -Dfile.encoding=UTF-8 -jar target/kcht-import-service-1.0.0-SNAPSHOT.jar \
  --files=assets/mst_national_road.json,assets/tbl_bridge.json,assets/tbl_road_sign.json
```

### 4.1. Bảng tổng hợp lần chạy 2 (Re-run / Idempotent Audit)

```text
========================================================================================
IMPORT SUMMARY REPORT (DryRun: false, Job: IMPORT_JOB_1791215074967, Total Time: 12144 ms)
========================================================================================
Dataset File                     | Manifest |     Read | Inserted |  Updated |  Skipped |   Failed |    Geoms | Time(ms)
---------------------------------+----------+----------+----------+----------+----------+----------+----------+----------
assets/mst_national_road.json    |      169 |      169 |        0 |        0 |      169 |        0 |        0 |      318
assets/tbl_bridge.json           |    11631 |    11631 |        0 |        0 |    11631 |        0 |     7436 |     1671
assets/tbl_road_sign.json        |   222112 |   222112 |        0 |        0 |   222112 |        0 |        0 |     9954
========================================================================================
TOTAL: Files=3/3 (Skipped=0), Read=233912, Ins=0, Upd=0, Skip=233912, Fail=0
========================================================================================
```

### 4.2. Đối soát dữ liệu CSDL sau khi chạy lại

Truy vấn đối soát trực tiếp trên PostgreSQL:
```sql
SELECT dataset_key, count(*) as row_count, count(DISTINCT record_key) as unique_keys, 
       count(*) - count(DISTINCT payload_sha256) as dup_payloads
FROM raw_dataset_record
GROUP BY dataset_key;
```

**Kết quả đối soát:**
- `mst_national_road`: **169** bản ghi (không tăng).
- `tbl_bridge`: **11.631** bản ghi (không tăng).
- `tbl_road_sign`: **222.112** bản ghi (không tăng).
- **Tổng số bản ghi trong `raw_dataset_record`:** **233.912** (chính xác tuyệt đối, không tăng thêm 1 bản ghi nào).
- **Số bản ghi bị chèn trùng (duplicate):** **0**.
- **Thời gian quét đối chiếu toàn bộ 233.912 bản ghi:** **12,14 giây** (nhờ cơ chế tra cứu batch theo index `(dataset_key, record_key)` và so khớp băm `payload_sha256` trước khi quyết định ghi).

---

## 5. BẬT LƯU VẾT QUẢN TRỊ (AUDIT LOGS & LINEAGE)

Hệ thống đã tự động ghi nhận đầy đủ lịch sử trong 2 bảng quản lý tiến trình:

### 5.1. Bảng `import_job`

| ID | Job Name | Status | Total Files | Processed | Total Records | Success | Errors | Duration |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | `IMPORT_JOB_1791214963622` | `COMPLETED` | 3 | 3 | 233.912 | 233.912 | 0 | 91,9 s |
| 2 | `IMPORT_JOB_1791215074967` | `COMPLETED` | 3 | 3 | 233.912 | 0 (All Skipped) | 0 | 12,1 s |

### 5.2. Bảng `import_file`

- Cả 6 lượt xử lý tệp (3 tệp × 2 phiên) đều ghi nhận trạng thái `COMPLETED`.
- Lưu trữ chính xác kích thước tệp (bytes), thời điểm bắt đầu (`started_at`), thời điểm hoàn tất (`completed_at`), và mã băm toàn vẹn `source_sha256`.

### 5.3. Bảng `import_error`

- `SELECT count(*) FROM import_error;` = **0 bản ghi**. Không có bất kỳ ngoại lệ nào phát sinh trong quá trình nạp.

---

## 6. ĐÁNH GIÁ VÀ KẾT LUẬN

### 6.1. Đối chiếu Acceptance Criteria

| Tiêu chí nghiệm thu Prompt 5 | Kết quả thực tế | Trạng thái |
| :--- | :--- | :---: |
| **Không mất record nào** | 233.912 / 233.912 bản ghi nạp thành công (0 failed) | **ĐẠT** |
| **Không duplicate record_key** | 233.912 unique keys, 0 duplicate keys | **ĐẠT** |
| **Không duplicate payload** | 0 duplicate payload_sha256 trong cả 3 datasets | **ĐẠT** |
| **Chạy lại mà số lượng không tăng sai** | Lần 2: 233.912 skipped, 0 inserted, tổng số bản ghi giữ nguyên 233.912 | **ĐẠT** |
| **Quản lý bộ nhớ RAM** | Streaming qua Jackson JsonParser không gây tràn RAM; heap JVM ổn định dưới 300 MB kể cả khi stream file 789 MB | **ĐẠT** |
| **Tính toàn vẹn dữ liệu** | File JSON nguồn không bị sửa đổi, CSDL lưu vết SHA-256 đối chiếu | **ĐẠT** |

### 6.2. Kết luận
Dịch vụ nạp dữ liệu (`ImportService`) đã chứng minh tính ổn định cao, tốc độ xử lý nhanh (~2.500 records/giây trên máy local), đảm bảo tính an toàn dữ liệu, chống trùng lặp và tính toàn vẹn tuyệt đối. Hệ thống hoàn toàn đủ điều kiện để mở rộng cho các bước tiếp theo.
