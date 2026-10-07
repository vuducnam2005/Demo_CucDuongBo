# BÁO CÁO NGHIỆM THU NẠP TOÀN BỘ DỮ LIỆU KCHT (FULL IMPORT REPORT)

> **Ngày thực hiện:** 2026-10-05  
> **Mã phiên chạy (Import Job):** `IMPORT_JOB_1791215586055` (ID: 11)  
> **Trạng thái tổng thể:** **THÀNH CÔNG (100% Hoàn tất - 0 Thất bại - 0 Lỗi bản ghi)**  
> **Dung lượng CSDL sau nạp:** **2279 MB**  
> **Thời gian thực thi:** 2026-10-05 15:53:06.087217+00 -> 2026-10-05 15:59:00.372517+00 (~5 phút 54 giây)  

---

## 1. TỔNG QUAN KẾT QUẢ NẠP DỮ LIỆU (EXECUTIVE SUMMARY)

| Chỉ tiêu kiểm tra | Kết quả ghi nhận | Ghi chú & Đánh giá |
| :--- | :--- | :--- |
| **Tổng số tệp dữ liệu xử lý** | **658 / 658 tệp (100%)** | Toàn bộ tệp trong `manifest.json` |
| **Số dataset thành công** | **658 tệp** | 100% tệp hoàn thành nạp |
| **Số dataset thất bại** | **0 tệp** | 0 tệp bị gián đoạn hoặc lỗi |
| **Số dataset có bản ghi lỗi** | **0 tệp** | Không có record nào vi phạm schema |
| **Số dataset rỗng (0 bản ghi)** | **195 tệp** | Các bảng chưa phát sinh số liệu nguồn |
| **Số dataset có dữ liệu** | **463 tệp** | Đều đã được lưu trữ vào CSDL |
| **Tổng số bản ghi đọc (`records_read`)** | **1,104,088 bản ghi** | Khớp 100% tổng số bản ghi trong manifest |
| **Số bản ghi nạp mới (`records_inserted`)** | **868,422 bản ghi** | Nạp mới trong phiên Job 11 |
| **Số bản ghi cập nhật (`records_updated`)** | **3 bản ghi** | Khử trùng lặp nội bộ và cập nhật payload mới |
| **Số bản ghi bỏ qua (`records_skipped`)** | **233,975 bản ghi** | Đã nạp từ Prompt 5 & 6, kiểm tra băm SHA256 trùng khớp |
| **Số bản ghi lỗi (`records_failed`)** | **0 bản ghi** | 0 lỗi |
| **Tổng bản ghi lưu trữ trong CSDL** | **1,102,397 bản ghi** | Bảng `raw_dataset_record` |
| **Số lượng bản ghi trùng lặp trong CSDL** | **0 bản ghi (0%)** | `COUNT(DISTINCT key) == COUNT(*)` |
| **Số bản ghi trùng khóa lọc từ nguồn** | **1,691 bản ghi** | Đã khử trùng và hợp nhất tự động (Xem mục 3) |
| **Số bản ghi có hình học hợp lệ** | **153,689 bản ghi** | PostGIS coordinates/geometries |
| **Dung lượng CSDL (`kcht_db`)** | **2279 MB** | Bao gồm data, index, jsonb, PostGIS |

---

## 2. ĐỐI SOÁT TÍNH TOÀN VẸN CƠ SỞ DỮ LIỆU (DATA INTEGRITY)

### 2.1. Kiểm tra chống trùng lặp (Uniqueness & Idempotency)
Truy vấn kiểm tra khóa duy nhất trên toàn bộ bảng `raw_dataset_record`:
```sql
SELECT 
    count(*) AS total_raw_records,
    count(DISTINCT dataset_key || '::' || record_key) AS distinct_records,
    count(*) - count(DISTINCT dataset_key || '::' || record_key) AS duplicate_count
FROM raw_dataset_record;
```
**Kết quả kiểm tra:**
- `total_raw_records`: **1,102,397**
- `distinct_records`: **1,102,397**
- `duplicate_count`: **0 (Tuyệt đối không trùng lặp)**

### 2.2. Kiểm tra tính lũy tiến (Idempotency Verification)
- Ở Prompt 5 và Prompt 6, hệ thống đã nạp trước 4 dataset (`mst_national_road.json` [169], `tbl_bridge.json` [11,631], `tbl_road_sign.json` [222,112] và `duonggom.json` [63]) với tổng cộng **233,975** bản ghi.
- Khi chạy Full Import (Prompt 7), Import Service đã sử dụng băm SHA256 của từng payload kết hợp tra cứu batch `record_key IN (...)`. Toàn bộ **233,975** bản ghi cũ đã được nhận diện tức thì và đánh dấu `records_skipped = 233,975`, không ghi đĩa dư thừa, không tạo duplicate.

---

## 3. GIẢI TRÌNH CHÊNH LỆCH BẢN GHI NGUỒN (RECONCILIATION DIFFERENCES)

Tổng số bản ghi theo `manifest.json` là **1,104,088**, trong khi số bản ghi lưu trữ thực tế trong bảng `raw_dataset_record` là **1,102,397** (chênh lệch **1,691** bản ghi).

Nguyên nhân: Có **8 dataset** trong dữ liệu nguồn gốc chứa các bản ghi bị trùng lặp khóa chính (`record_key` / `vidagis_id`) ngay trong cùng một file JSON. Import Service với cơ chế deduplication đã tự động phát hiện, cập nhật payload mới nhất và khử trùng lặp để bảo đảm tính toàn vẹn dữ liệu:

| Tệp nguồn dữ liệu | Phân loại | Manifest | Đọc | Nạp mới | Cập nhật | Bỏ qua | CSDL thực tế | Chênh lệch (Trùng lặp nguồn) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `modules/documents.json` | module | 309 | 309 | 250 | 0 | 0 | 250 | **59** |
| `modules/documents_group_kqldb_3.json` | module | 10 | 10 | 8 | 0 | 0 | 8 | **2** |
| `modules/documents_group_kqldb_4.json` | module | 19 | 19 | 15 | 0 | 0 | 15 | **4** |
| `modules/documents_group_root.json` | module | 309 | 309 | 250 | 0 | 0 | 250 | **59** |
| `modules/documents_group_sxd_dt.json` | module | 2 | 2 | 1 | 0 | 0 | 1 | **1** |
| `modules/documents_group_sxd_kh.json` | module | 2 | 2 | 1 | 0 | 0 | 1 | **1** |
| `modules/documents_group_sxd_tpct.json` | module | 4 | 4 | 3 | 0 | 0 | 3 | **1** |
| `modules/user_guide_tables.json` | module | 1,620 | 1,620 | 56 | 3 | 0 | 56 | **1,564** |

> [!NOTE]
> **Ví dụ điển hình:** Tệp `modules/documents_group_sxd_kh.json` chứa 2 bản ghi nhưng cả 2 bản ghi đều có `"vidagis_id": "1785752096858455"` (cùng trỏ vào 1 tài liệu kỹ thuật). Tệp `modules/user_guide_tables.json` chứa 1,620 dòng định nghĩa bảng nhưng thực chất chỉ có 56 bảng danh mục duy nhất. Việc khử trùng lặp giúp cơ sở dữ liệu hoàn toàn chuẩn hóa và tránh lỗi vi phạm khóa duy nhất.

---

## 4. BÁO CÁO HÌNH HỌC VÀ KHÔNG GIAN (POSTGIS & GEOMETRY)

- **Tổng số bản ghi có tọa độ không gian hợp lệ (`validGeometries`):** **153,689 bản ghi**.
  - Gồm các đối tượng tài sản có tọa độ `x_min`/`y_min` (VN-2000 hoặc WGS84) như `tbl_bridge`, `tbl_road_sign`, cầu, trạm cân, điểm sạt lở, cột mốc.
- **Các bản ghi phi không gian hoặc metadata thuộc tính:** **948,708 bản ghi**.
- **Dữ liệu `"geom": "geom"`:** 830,836 bản ghi chứa chuỗi placeholder này do phần mềm VidaGIS xuất khẩu thuộc tính mà không đính kèm tọa độ WKT/GeoJSON. Lớp curated sẽ xử lý ánh xạ hình học từ các bảng trạm/cầu/tuyến đường liên quan.
- **Số lỗi Geometry làm hỏng import:** **0 lỗi**.

---

## 5. HƯỚNG DẪN DỪNG AN TOÀN VÀ LỆNH RESUME (OPERATIONAL RUNBOOK)

Hệ thống nạp dữ liệu được thiết kế Idempotent và có khả năng phục hồi tự động:

### 5.1. Kiểm tra trạng thái các phiên nạp
```bash
docker exec -i kcht_postgres psql -U kcht_user -d kcht_db -c "SELECT id, job_name, status, total_files, processed_files, total_records, success_records, error_records FROM import_job ORDER BY id DESC;"
```

### 5.2. Lệnh nạp bù / Resume khi cần tiếp tục
Nếu tiến trình bị dừng đột ngột giữa chừng (mất điện, kill process), chỉ cần chạy lại lệnh resume:
```powershell
java -Xms512m -Xmx2048m "-Dfile.encoding=UTF-8" "-Dkcht.import.batch-size=500" -jar target\kcht-import-service-1.0.0-SNAPSHOT.jar --all
```
- Import Service sẽ tự động bỏ qua các tệp đã có trạng thái `COMPLETED` trong phiên hiện tại.
- Đối với các tệp đang chạy dở, cơ chế `ON CONFLICT` và so khớp băm SHA256 sẽ tự động bỏ qua các bản ghi đã lưu, chỉ nạp tiếp các bản ghi còn lại mà không bao giờ sinh duplicate.

---

## 6. BẢNG ĐỐI SOÁT CHI TIẾT 658 DATASET (FULL RECONCILIATION TABLE)

| STT | Tệp dữ liệu (`file_path`) | Phân loại | Manifest | Đọc | Nạp mới | Bỏ qua | Lỗi | CSDL thực tế | Geoms | TG (ms) | Tốc độ (rec/s) | Trạng thái |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | `assets/duonggom.json` | asset | 63 | 63 | 0 | 63 | 0 | 63 | 0 | 332 | 189.8 | ✅ THÀNH CÔNG |
| 2 | `assets/duongnhanh.json` | asset | 360 | 360 | 360 | 0 | 0 | 360 | 0 | 371 | 970.4 | ✅ THÀNH CÔNG |
| 3 | `assets/duongtranh.json` | asset | 67 | 67 | 67 | 0 | 0 | 67 | 0 | 86 | 779.1 | ✅ THÀNH CÔNG |
| 4 | `assets/gp_ctdkt.json` | asset | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 34 | 29.4 | ✅ THÀNH CÔNG |
| 5 | `assets/mst_commune_road.json` | asset | 14,394 | 14,394 | 14,394 | 0 | 0 | 14,394 | 0 | 5,479 | 2,627.1 | ✅ THÀNH CÔNG |
| 6 | `assets/mst_counting_station.json` | asset | 372 | 372 | 372 | 0 | 0 | 372 | 0 | 137 | 2,715.3 | ✅ THÀNH CÔNG |
| 7 | `assets/mst_national_expressway.json` | asset | 51 | 51 | 51 | 0 | 0 | 51 | 0 | 38 | 1,342.1 | ✅ THÀNH CÔNG |
| 8 | `assets/mst_national_road.json` | asset | 169 | 169 | 0 | 169 | 0 | 169 | 0 | 33 | 5,121.2 | ✅ THÀNH CÔNG |
| 9 | `assets/mst_provincial_road.json` | asset | 1,425 | 1,425 | 1,425 | 0 | 0 | 1,425 | 0 | 660 | 2,159.1 | ✅ THÀNH CÔNG |
| 10 | `assets/mst_special_road.json` | asset | 175 | 175 | 175 | 0 | 0 | 175 | 0 | 86 | 2,034.9 | ✅ THÀNH CÔNG |
| 11 | `assets/mst_urban_road.json` | asset | 20,120 | 20,120 | 20,120 | 0 | 0 | 20,120 | 0 | 6,696 | 3,004.8 | ✅ THÀNH CÔNG |
| 12 | `assets/mst_village_road.json` | asset | 28,597 | 28,597 | 28,597 | 0 | 0 | 28,597 | 0 | 8,062 | 3,547.1 | ✅ THÀNH CÔNG |
| 13 | `assets/road_sphere_mirror.json` | asset | 191,928 | 191,928 | 191,928 | 0 | 0 | 191,928 | 52,196 | 72,263 | 2,656.0 | ✅ THÀNH CÔNG |
| 14 | `assets/tbl_bridge.json` | asset | 11,631 | 11,631 | 0 | 11,631 | 0 | 11,631 | 7,436 | 2,320 | 5,013.4 | ✅ THÀNH CÔNG |
| 15 | `assets/tbl_bus_station.json` | asset | 387 | 387 | 387 | 0 | 0 | 387 | 380 | 127 | 3,047.2 | ✅ THÀNH CÔNG |
| 16 | `assets/tbl_bus_stops.json` | asset | 5,418 | 5,418 | 5,418 | 0 | 0 | 5,418 | 5,383 | 1,689 | 3,207.8 | ✅ THÀNH CÔNG |
| 17 | `assets/tbl_disaster_res_facility.json` | asset | 20 | 20 | 20 | 0 | 0 | 20 | 16 | 45 | 444.4 | ✅ THÀNH CÔNG |
| 18 | `assets/tbl_ferry.json` | asset | 15 | 15 | 15 | 0 | 0 | 15 | 0 | 35 | 428.6 | ✅ THÀNH CÔNG |
| 19 | `assets/tbl_ferry_terminal.json` | asset | 15 | 15 | 15 | 0 | 0 | 15 | 0 | 37 | 405.4 | ✅ THÀNH CÔNG |
| 20 | `assets/tbl_first_aid_station.json` | asset | 235 | 235 | 235 | 0 | 0 | 235 | 224 | 90 | 2,611.1 | ✅ THÀNH CÔNG |
| 21 | `assets/tbl_guardrail.json` | asset | 51,697 | 51,697 | 51,697 | 0 | 0 | 51,697 | 0 | 19,065 | 2,711.6 | ✅ THÀNH CÔNG |
| 22 | `assets/tbl_guide_post.json` | asset | 37,042 | 37,042 | 37,042 | 0 | 0 | 37,042 | 0 | 14,979 | 2,472.9 | ✅ THÀNH CÔNG |
| 23 | `assets/tbl_infrastructure_row.json` | asset | 14,656 | 14,656 | 14,656 | 0 | 0 | 14,656 | 0 | 7,759 | 1,888.9 | ✅ THÀNH CÔNG |
| 24 | `assets/tbl_intersection.json` | asset | 7,053 | 7,053 | 7,053 | 0 | 0 | 7,053 | 7,034 | 2,387 | 2,954.8 | ✅ THÀNH CÔNG |
| 25 | `assets/tbl_its.json` | asset | 10 | 10 | 10 | 0 | 0 | 10 | 10 | 34 | 294.1 | ✅ THÀNH CÔNG |
| 26 | `assets/tbl_km_post.json` | asset | 21,910 | 21,910 | 21,910 | 0 | 0 | 21,910 | 21,755 | 8,370 | 2,617.7 | ✅ THÀNH CÔNG |
| 27 | `assets/tbl_land_btra.json` | asset | 12 | 12 | 12 | 0 | 0 | 12 | 11 | 37 | 324.3 | ✅ THÀNH CÔNG |
| 28 | `assets/tbl_longitudinal.json` | asset | 61,161 | 61,161 | 61,161 | 0 | 0 | 61,161 | 0 | 28,821 | 2,122.1 | ✅ THÀNH CÔNG |
| 29 | `assets/tbl_median_strip.json` | asset | 6,960 | 6,960 | 6,960 | 0 | 0 | 6,960 | 0 | 2,340 | 2,974.4 | ✅ THÀNH CÔNG |
| 30 | `assets/tbl_noise_barrier.json` | asset | 24 | 24 | 24 | 0 | 0 | 24 | 0 | 44 | 545.5 | ✅ THÀNH CÔNG |
| 31 | `assets/tbl_overlaproad.json` | asset | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 33 | 90.9 | ✅ THÀNH CÔNG |
| 32 | `assets/tbl_pierstr.json` | asset | 14,948 | 14,948 | 14,948 | 0 | 0 | 14,948 | 0 | 6,975 | 2,143.1 | ✅ THÀNH CÔNG |
| 33 | `assets/tbl_pontoon_bridge.json` | asset | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 35 | 57.1 | ✅ THÀNH CÔNG |
| 34 | `assets/tbl_railway_crossing.json` | asset | 74 | 74 | 74 | 0 | 0 | 74 | 67 | 58 | 1,275.9 | ✅ THÀNH CÔNG |
| 35 | `assets/tbl_rescue_vehicle.json` | asset | 8 | 8 | 8 | 0 | 0 | 8 | 7 | 38 | 210.5 | ✅ THÀNH CÔNG |
| 36 | `assets/tbl_rest_stops.json` | asset | 71 | 71 | 71 | 0 | 0 | 71 | 71 | 70 | 1,014.3 | ✅ THÀNH CÔNG |
| 37 | `assets/tbl_retaining_wall.json` | asset | 9,856 | 9,856 | 9,856 | 0 | 0 | 9,856 | 0 | 3,946 | 2,497.7 | ✅ THÀNH CÔNG |
| 38 | `assets/tbl_rmd.json` | asset | 6,708 | 6,708 | 6,708 | 0 | 0 | 6,708 | 0 | 3,674 | 1,825.8 | ✅ THÀNH CÔNG |
| 39 | `assets/tbl_rmd_foreign_road.json` | asset | 10 | 10 | 10 | 0 | 0 | 10 | 0 | 45 | 222.2 | ✅ THÀNH CÔNG |
| 40 | `assets/tbl_road_admin_office.json` | asset | 372 | 372 | 372 | 0 | 0 | 372 | 361 | 141 | 2,638.3 | ✅ THÀNH CÔNG |
| 41 | `assets/tbl_road_sign.json` | asset | 222,112 | 222,112 | 0 | 222,112 | 0 | 222,112 | 0 | 19,474 | 11,405.6 | ✅ THÀNH CÔNG |
| 42 | `assets/tbl_segment.json` | asset | 655 | 655 | 655 | 0 | 0 | 655 | 0 | 951 | 688.7 | ✅ THÀNH CÔNG |
| 43 | `assets/tbl_slope.json` | asset | 11,099 | 11,099 | 11,099 | 0 | 0 | 11,099 | 0 | 4,414 | 2,514.5 | ✅ THÀNH CÔNG |
| 44 | `assets/tbl_spill_way.json` | asset | 128 | 128 | 128 | 0 | 0 | 128 | 127 | 81 | 1,580.2 | ✅ THÀNH CÔNG |
| 45 | `assets/tbl_street_lighting.json` | asset | 4,948 | 4,948 | 4,948 | 0 | 0 | 4,948 | 0 | 1,922 | 2,574.4 | ✅ THÀNH CÔNG |
| 46 | `assets/tbl_toll_booth.json` | asset | 97 | 97 | 97 | 0 | 0 | 97 | 94 | 82 | 1,182.9 | ✅ THÀNH CÔNG |
| 47 | `assets/tbl_transverse_drainage.json` | asset | 60,716 | 60,716 | 60,716 | 0 | 0 | 60,716 | 57,682 | 46,241 | 1,313.0 | ✅ THÀNH CÔNG |
| 48 | `assets/tbl_tunnel_main.json` | asset | 34 | 34 | 34 | 0 | 0 | 34 | 0 | 53 | 641.5 | ✅ THÀNH CÔNG |
| 49 | `assets/tbl_underpass_box.json` | asset | 808 | 808 | 808 | 0 | 0 | 808 | 806 | 423 | 1,910.2 | ✅ THÀNH CÔNG |
| 50 | `assets/tbl_unstr.json` | asset | 21,463 | 21,463 | 21,463 | 0 | 0 | 21,463 | 0 | 8,611 | 2,492.5 | ✅ THÀNH CÔNG |
| 51 | `assets/thongtinduanbot.json` | asset | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 24 | 250.0 | ✅ THÀNH CÔNG |
| 52 | `assets/thongtinlandungkhancap.json` | asset | 13 | 13 | 13 | 0 | 0 | 13 | 0 | 30 | 433.3 | ✅ THÀNH CÔNG |
| 53 | `assets/thongtintaitrong.json` | asset | 696 | 696 | 696 | 0 | 0 | 696 | 0 | 156 | 4,461.5 | ✅ THÀNH CÔNG |
| 54 | `assets/tunnel_emergency.json` | asset | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 55 | `assets/tunnel_other.json` | asset | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 34 | 117.6 | ✅ THÀNH CÔNG |
| 56 | `assets/tunnel_work_outside.json` | asset | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 31 | 129.0 | ✅ THÀNH CÔNG |
| 57 | `assets/weight_station.json` | asset | 29 | 29 | 29 | 0 | 0 | 29 | 29 | 36 | 805.6 | ✅ THÀNH CÔNG |
| 58 | `document_files_index.json` | module | 309 | 309 | 309 | 0 | 0 | 309 | 0 | 71 | 4,352.1 | ✅ THÀNH CÔNG |
| 59 | `modules/annual_plans.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 60 | `modules/document_folders.json` | module | 121 | 121 | 121 | 0 | 0 | 121 | 0 | 41 | 2,951.2 | ✅ THÀNH CÔNG |
| 61 | `modules/documents.json` | module | 309 | 309 | 250 | 0 | 0 | 250 | 0 | 80 | 3,862.5 | ✅ THÀNH CÔNG |
| 62 | `modules/documents_group_cdb_vn.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 25 | 160.0 | ✅ THÀNH CÔNG |
| 63 | `modules/documents_group_g_10.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 28 | 0.0 | ✅ THÀNH CÔNG |
| 64 | `modules/documents_group_g_11.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 29 | 0.0 | ✅ THÀNH CÔNG |
| 65 | `modules/documents_group_g_12.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 66 | `modules/documents_group_g_13.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 67 | `modules/documents_group_g_14.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0 | ✅ THÀNH CÔNG |
| 68 | `modules/documents_group_g_15.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0.0 | ✅ THÀNH CÔNG |
| 69 | `modules/documents_group_g_16.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 70 | `modules/documents_group_g_17.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 71 | `modules/documents_group_g_17_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 72 | `modules/documents_group_g_17_1_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 73 | `modules/documents_group_g_17_1_1_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 74 | `modules/documents_group_g_17_1_1_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 75 | `modules/documents_group_g_17_1_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 76 | `modules/documents_group_g_17_1_2_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 77 | `modules/documents_group_g_17_1_3.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0 | ✅ THÀNH CÔNG |
| 78 | `modules/documents_group_g_18.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 79 | `modules/documents_group_g_18_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 80 | `modules/documents_group_g_18_1_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 81 | `modules/documents_group_g_19.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 82 | `modules/documents_group_g_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 83 | `modules/documents_group_g_20.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 84 | `modules/documents_group_g_21.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 85 | `modules/documents_group_g_22.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 86 | `modules/documents_group_g_23.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 25 | 0.0 | ✅ THÀNH CÔNG |
| 87 | `modules/documents_group_g_24.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0 | ✅ THÀNH CÔNG |
| 88 | `modules/documents_group_g_25.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 89 | `modules/documents_group_g_26.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 90 | `modules/documents_group_g_27.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 91 | `modules/documents_group_g_27_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 92 | `modules/documents_group_g_28.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 93 | `modules/documents_group_g_28_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 94 | `modules/documents_group_g_29.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 95 | `modules/documents_group_g_29_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0.0 | ✅ THÀNH CÔNG |
| 96 | `modules/documents_group_g_3.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 97 | `modules/documents_group_g_30.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 98 | `modules/documents_group_g_30_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 99 | `modules/documents_group_g_30_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 100 | `modules/documents_group_g_30_3.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 101 | `modules/documents_group_g_30_4.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 102 | `modules/documents_group_g_30_5.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 103 | `modules/documents_group_g_31.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 25 | 0.0 | ✅ THÀNH CÔNG |
| 104 | `modules/documents_group_g_32.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 105 | `modules/documents_group_g_33.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 106 | `modules/documents_group_g_33_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 107 | `modules/documents_group_g_34.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 108 | `modules/documents_group_g_35.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 109 | `modules/documents_group_g_35_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 110 | `modules/documents_group_g_35_1_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 111 | `modules/documents_group_g_36.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 112 | `modules/documents_group_g_36_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 113 | `modules/documents_group_g_36_10.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 114 | `modules/documents_group_g_36_11.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 115 | `modules/documents_group_g_36_12.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 116 | `modules/documents_group_g_36_13.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 117 | `modules/documents_group_g_36_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 118 | `modules/documents_group_g_36_3.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 24 | 0.0 | ✅ THÀNH CÔNG |
| 119 | `modules/documents_group_g_36_4.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 120 | `modules/documents_group_g_36_5.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 121 | `modules/documents_group_g_36_6.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 122 | `modules/documents_group_g_36_7.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 123 | `modules/documents_group_g_36_8.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 124 | `modules/documents_group_g_36_9.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 125 | `modules/documents_group_g_37.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 126 | `modules/documents_group_g_38.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0 | ✅ THÀNH CÔNG |
| 127 | `modules/documents_group_g_39.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 24 | 0.0 | ✅ THÀNH CÔNG |
| 128 | `modules/documents_group_g_4.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 32 | 0.0 | ✅ THÀNH CÔNG |
| 129 | `modules/documents_group_g_40.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 130 | `modules/documents_group_g_40_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 131 | `modules/documents_group_g_40_2.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 132 | `modules/documents_group_g_40_3.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 133 | `modules/documents_group_g_40_4.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 134 | `modules/documents_group_g_40_5.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 135 | `modules/documents_group_g_40_6.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 136 | `modules/documents_group_g_40_7.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 137 | `modules/documents_group_g_41.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 138 | `modules/documents_group_g_41_1.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 139 | `modules/documents_group_g_5.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 140 | `modules/documents_group_g_6.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 141 | `modules/documents_group_g_7.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 142 | `modules/documents_group_g_8.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0 | ✅ THÀNH CÔNG |
| 143 | `modules/documents_group_g_9.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 144 | `modules/documents_group_kqldb_1.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 20 | 400.0 | ✅ THÀNH CÔNG |
| 145 | `modules/documents_group_kqldb_2.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 21 | 381.0 | ✅ THÀNH CÔNG |
| 146 | `modules/documents_group_kqldb_3.json` | module | 10 | 10 | 8 | 0 | 0 | 8 | 0 | 21 | 476.2 | ✅ THÀNH CÔNG |
| 147 | `modules/documents_group_kqldb_4.json` | module | 19 | 19 | 15 | 0 | 0 | 15 | 0 | 24 | 791.7 | ✅ THÀNH CÔNG |
| 148 | `modules/documents_group_root.json` | module | 309 | 309 | 250 | 0 | 0 | 250 | 0 | 65 | 4,753.8 | ✅ THÀNH CÔNG |
| 149 | `modules/documents_group_sxd_ag.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 150 | `modules/documents_group_sxd_bn.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 151 | `modules/documents_group_sxd_cb.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 152 | `modules/documents_group_sxd_cm.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 153 | `modules/documents_group_sxd_db.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 154 | `modules/documents_group_sxd_dl.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 155 | `modules/documents_group_sxd_dn.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 156 | `modules/documents_group_sxd_dt.json` | module | 2 | 2 | 1 | 0 | 0 | 1 | 0 | 23 | 87.0 | ✅ THÀNH CÔNG |
| 157 | `modules/documents_group_sxd_gl.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 158 | `modules/documents_group_sxd_ht.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 159 | `modules/documents_group_sxd_hy.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 160 | `modules/documents_group_sxd_kh.json` | module | 2 | 2 | 1 | 0 | 0 | 1 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 161 | `modules/documents_group_sxd_lc.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 25 | 80.0 | ✅ THÀNH CÔNG |
| 162 | `modules/documents_group_sxd_lch.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 163 | `modules/documents_group_sxd_ld.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 164 | `modules/documents_group_sxd_ls.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 165 | `modules/documents_group_sxd_na.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 166 | `modules/documents_group_sxd_nb.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 21 | 95.2 | ✅ THÀNH CÔNG |
| 167 | `modules/documents_group_sxd_pt.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 168 | `modules/documents_group_sxd_qn.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 169 | `modules/documents_group_sxd_qni.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0.0 | ✅ THÀNH CÔNG |
| 170 | `modules/documents_group_sxd_qt.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 171 | `modules/documents_group_sxd_sl.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 31 | 0.0 | ✅ THÀNH CÔNG |
| 172 | `modules/documents_group_sxd_th.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 24 | 83.3 | ✅ THÀNH CÔNG |
| 173 | `modules/documents_group_sxd_thn.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 174 | `modules/documents_group_sxd_tn.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 175 | `modules/documents_group_sxd_tpct.json` | module | 4 | 4 | 3 | 0 | 0 | 3 | 0 | 19 | 210.5 | ✅ THÀNH CÔNG |
| 176 | `modules/documents_group_sxd_tpdn.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 25 | 40.0 | ✅ THÀNH CÔNG |
| 177 | `modules/documents_group_sxd_tph.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 178 | `modules/documents_group_sxd_tphcm.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 29 | 0.0 | ✅ THÀNH CÔNG |
| 179 | `modules/documents_group_sxd_tphn.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 24 | 0.0 | ✅ THÀNH CÔNG |
| 180 | `modules/documents_group_sxd_tphp.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 181 | `modules/documents_group_sxd_tq.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 29 | 69.0 | ✅ THÀNH CÔNG |
| 182 | `modules/documents_group_sxd_vl.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 45 | 0.0 | ✅ THÀNH CÔNG |
| 183 | `modules/maintenance_baidoxe_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 28 | 35.7 | ✅ THÀNH CÔNG |
| 184 | `modules/maintenance_baidoxe_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 185 | `modules/maintenance_detail_baidoxe_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 186 | `modules/maintenance_detail_baidoxe_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 187 | `modules/maintenance_detail_duonggom_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 188 | `modules/maintenance_detail_duonggom_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 189 | `modules/maintenance_detail_duongnhanh_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 190 | `modules/maintenance_detail_duongnhanh_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 191 | `modules/maintenance_detail_duongtranh_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 192 | `modules/maintenance_detail_duongtranh_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 193 | `modules/maintenance_detail_gp_csdtttv_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 194 | `modules/maintenance_detail_gp_csdtttv_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 195 | `modules/maintenance_detail_gp_ctdkt_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 196 | `modules/maintenance_detail_gp_ctdkt_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 197 | `modules/maintenance_detail_gp_ldvh_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 198 | `modules/maintenance_detail_gp_ldvh_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 199 | `modules/maintenance_detail_gp_tcngdn_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 200 | `modules/maintenance_detail_gp_tcngdn_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 201 | `modules/maintenance_detail_gp_tkngdn_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 202 | `modules/maintenance_detail_gp_tkngdn_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 203 | `modules/maintenance_detail_gp_ttv_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 204 | `modules/maintenance_detail_gp_ttv_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 205 | `modules/maintenance_detail_mst_commune_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 206 | `modules/maintenance_detail_mst_commune_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 207 | `modules/maintenance_detail_mst_counting_station_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 208 | `modules/maintenance_detail_mst_counting_station_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 209 | `modules/maintenance_detail_mst_national_expressway_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 210 | `modules/maintenance_detail_mst_national_expressway_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 211 | `modules/maintenance_detail_mst_national_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 212 | `modules/maintenance_detail_mst_national_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 213 | `modules/maintenance_detail_mst_provincial_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 214 | `modules/maintenance_detail_mst_provincial_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 29 | 0.0 | ✅ THÀNH CÔNG |
| 215 | `modules/maintenance_detail_mst_special_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 26 | 0.0 | ✅ THÀNH CÔNG |
| 216 | `modules/maintenance_detail_mst_special_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 25 | 0.0 | ✅ THÀNH CÔNG |
| 217 | `modules/maintenance_detail_mst_urban_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 29 | 0.0 | ✅ THÀNH CÔNG |
| 218 | `modules/maintenance_detail_mst_urban_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 30 | 0.0 | ✅ THÀNH CÔNG |
| 219 | `modules/maintenance_detail_mst_village_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 24 | 0.0 | ✅ THÀNH CÔNG |
| 220 | `modules/maintenance_detail_mst_village_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 23 | 0.0 | ✅ THÀNH CÔNG |
| 221 | `modules/maintenance_detail_rmdinventory_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 222 | `modules/maintenance_detail_rmdinventory_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 28 | 0.0 | ✅ THÀNH CÔNG |
| 223 | `modules/maintenance_detail_road_sphere_mirror_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 224 | `modules/maintenance_detail_road_sphere_mirror_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 225 | `modules/maintenance_detail_tbl_bridge_chitiet.json` | module | 688 | 688 | 688 | 0 | 0 | 688 | 0 | 97 | 7,092.8 | ✅ THÀNH CÔNG |
| 226 | `modules/maintenance_detail_tbl_bridge_tonghop.json` | module | 688 | 688 | 688 | 0 | 0 | 688 | 0 | 124 | 5,548.4 | ✅ THÀNH CÔNG |
| 227 | `modules/maintenance_detail_tbl_bus_station_chitiet.json` | module | 335 | 335 | 335 | 0 | 0 | 335 | 0 | 64 | 5,234.4 | ✅ THÀNH CÔNG |
| 228 | `modules/maintenance_detail_tbl_bus_station_tonghop.json` | module | 335 | 335 | 335 | 0 | 0 | 335 | 0 | 57 | 5,877.2 | ✅ THÀNH CÔNG |
| 229 | `modules/maintenance_detail_tbl_bus_stops_chitiet.json` | module | 4,779 | 4,779 | 4,779 | 0 | 0 | 4,779 | 0 | 666 | 7,175.7 | ✅ THÀNH CÔNG |
| 230 | `modules/maintenance_detail_tbl_bus_stops_tonghop.json` | module | 4,779 | 4,779 | 4,779 | 0 | 0 | 4,779 | 0 | 472 | 10,125.0 | ✅ THÀNH CÔNG |
| 231 | `modules/maintenance_detail_tbl_disaster_res_facility_chitiet.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 18 | 500.0 | ✅ THÀNH CÔNG |
| 232 | `modules/maintenance_detail_tbl_disaster_res_facility_tonghop.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 29 | 310.3 | ✅ THÀNH CÔNG |
| 233 | `modules/maintenance_detail_tbl_ferry_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 234 | `modules/maintenance_detail_tbl_ferry_extend_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 235 | `modules/maintenance_detail_tbl_ferry_extend_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 236 | `modules/maintenance_detail_tbl_ferry_terminal_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 237 | `modules/maintenance_detail_tbl_ferry_terminal_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 238 | `modules/maintenance_detail_tbl_ferry_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 239 | `modules/maintenance_detail_tbl_first_aid_station_chitiet.json` | module | 189 | 189 | 189 | 0 | 0 | 189 | 0 | 44 | 4,295.5 | ✅ THÀNH CÔNG |
| 240 | `modules/maintenance_detail_tbl_first_aid_station_tonghop.json` | module | 189 | 189 | 189 | 0 | 0 | 189 | 0 | 40 | 4,725.0 | ✅ THÀNH CÔNG |
| 241 | `modules/maintenance_detail_tbl_guardrail_chitiet.json` | module | 50,443 | 50,443 | 50,443 | 0 | 0 | 50,443 | 0 | 12,861 | 3,922.2 | ✅ THÀNH CÔNG |
| 242 | `modules/maintenance_detail_tbl_guardrail_tonghop.json` | module | 14,936 | 14,936 | 14,936 | 0 | 0 | 14,936 | 0 | 2,214 | 6,746.2 | ✅ THÀNH CÔNG |
| 243 | `modules/maintenance_detail_tbl_guide_post_chitiet.json` | module | 30,242 | 30,242 | 30,242 | 0 | 0 | 30,242 | 0 | 9,766 | 3,096.7 | ✅ THÀNH CÔNG |
| 244 | `modules/maintenance_detail_tbl_guide_post_tonghop.json` | module | 30,242 | 30,242 | 30,242 | 0 | 0 | 30,242 | 0 | 9,007 | 3,357.6 | ✅ THÀNH CÔNG |
| 245 | `modules/maintenance_detail_tbl_infrastructure_row_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 41 | 0.0 | ✅ THÀNH CÔNG |
| 246 | `modules/maintenance_detail_tbl_infrastructure_row_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 108 | 0.0 | ✅ THÀNH CÔNG |
| 247 | `modules/maintenance_detail_tbl_intersection_chitiet.json` | module | 6,454 | 6,454 | 6,454 | 0 | 0 | 6,454 | 0 | 2,610 | 2,472.8 | ✅ THÀNH CÔNG |
| 248 | `modules/maintenance_detail_tbl_intersection_tonghop.json` | module | 6,454 | 6,454 | 6,454 | 0 | 0 | 6,454 | 0 | 1,126 | 5,731.8 | ✅ THÀNH CÔNG |
| 249 | `modules/maintenance_detail_tbl_its_chitiet.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 57 | 35.1 | ✅ THÀNH CÔNG |
| 250 | `modules/maintenance_detail_tbl_its_tonghop.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 66 | 30.3 | ✅ THÀNH CÔNG |
| 251 | `modules/maintenance_detail_tbl_km_post_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 41 | 0.0 | ✅ THÀNH CÔNG |
| 252 | `modules/maintenance_detail_tbl_km_post_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 43 | 0.0 | ✅ THÀNH CÔNG |
| 253 | `modules/maintenance_detail_tbl_land_btra_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 254 | `modules/maintenance_detail_tbl_land_btra_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 255 | `modules/maintenance_detail_tbl_longitudinal_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 33 | 0.0 | ✅ THÀNH CÔNG |
| 256 | `modules/maintenance_detail_tbl_longitudinal_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 30 | 0.0 | ✅ THÀNH CÔNG |
| 257 | `modules/maintenance_detail_tbl_median_strip_chitiet.json` | module | 6,723 | 6,723 | 6,723 | 0 | 0 | 6,723 | 0 | 1,143 | 5,881.9 | ✅ THÀNH CÔNG |
| 258 | `modules/maintenance_detail_tbl_median_strip_tonghop.json` | module | 6,723 | 6,723 | 6,723 | 0 | 0 | 6,723 | 0 | 724 | 9,285.9 | ✅ THÀNH CÔNG |
| 259 | `modules/maintenance_detail_tbl_noise_barrier_chitiet.json` | module | 23 | 23 | 23 | 0 | 0 | 23 | 0 | 27 | 851.9 | ✅ THÀNH CÔNG |
| 260 | `modules/maintenance_detail_tbl_noise_barrier_tonghop.json` | module | 23 | 23 | 23 | 0 | 0 | 23 | 0 | 23 | 1,000.0 | ✅ THÀNH CÔNG |
| 261 | `modules/maintenance_detail_tbl_overlaproad_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 262 | `modules/maintenance_detail_tbl_overlaproad_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 263 | `modules/maintenance_detail_tbl_pierstr_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 25 | 0.0 | ✅ THÀNH CÔNG |
| 264 | `modules/maintenance_detail_tbl_pierstr_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0.0 | ✅ THÀNH CÔNG |
| 265 | `modules/maintenance_detail_tbl_pontoon_bridge_chitiet.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 24 | 83.3 | ✅ THÀNH CÔNG |
| 266 | `modules/maintenance_detail_tbl_pontoon_bridge_tonghop.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 267 | `modules/maintenance_detail_tbl_potential_black_spot_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 268 | `modules/maintenance_detail_tbl_potential_black_spot_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 269 | `modules/maintenance_detail_tbl_railway_crossing_chitiet.json` | module | 65 | 65 | 65 | 0 | 0 | 65 | 0 | 32 | 2,031.3 | ✅ THÀNH CÔNG |
| 270 | `modules/maintenance_detail_tbl_railway_crossing_tonghop.json` | module | 65 | 65 | 65 | 0 | 0 | 65 | 0 | 29 | 2,241.4 | ✅ THÀNH CÔNG |
| 271 | `modules/maintenance_detail_tbl_remediation_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 272 | `modules/maintenance_detail_tbl_remediation_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 53 | 0.0 | ✅ THÀNH CÔNG |
| 273 | `modules/maintenance_detail_tbl_rescue_vehicle_chitiet.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 27 | 148.1 | ✅ THÀNH CÔNG |
| 274 | `modules/maintenance_detail_tbl_rescue_vehicle_tonghop.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 27 | 148.1 | ✅ THÀNH CÔNG |
| 275 | `modules/maintenance_detail_tbl_rest_stops_chitiet.json` | module | 55 | 55 | 55 | 0 | 0 | 55 | 0 | 32 | 1,718.8 | ✅ THÀNH CÔNG |
| 276 | `modules/maintenance_detail_tbl_rest_stops_tonghop.json` | module | 55 | 55 | 55 | 0 | 0 | 55 | 0 | 28 | 1,964.3 | ✅ THÀNH CÔNG |
| 277 | `modules/maintenance_detail_tbl_retaining_wall_chitiet.json` | module | 9,401 | 9,401 | 9,401 | 0 | 0 | 9,401 | 0 | 2,736 | 3,436.0 | ✅ THÀNH CÔNG |
| 278 | `modules/maintenance_detail_tbl_retaining_wall_tonghop.json` | module | 9,401 | 9,401 | 9,401 | 0 | 0 | 9,401 | 0 | 779 | 12,068.0 | ✅ THÀNH CÔNG |
| 279 | `modules/maintenance_detail_tbl_rmd_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 280 | `modules/maintenance_detail_tbl_rmd_foreign_road_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 281 | `modules/maintenance_detail_tbl_rmd_foreign_road_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 282 | `modules/maintenance_detail_tbl_rmd_socio_economic_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 283 | `modules/maintenance_detail_tbl_rmd_socio_economic_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 26 | 0.0 | ✅ THÀNH CÔNG |
| 284 | `modules/maintenance_detail_tbl_rmd_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 285 | `modules/maintenance_detail_tbl_road_admin_office_chitiet.json` | module | 241 | 241 | 241 | 0 | 0 | 241 | 0 | 38 | 6,342.1 | ✅ THÀNH CÔNG |
| 286 | `modules/maintenance_detail_tbl_road_admin_office_tonghop.json` | module | 241 | 241 | 241 | 0 | 0 | 241 | 0 | 32 | 7,531.3 | ✅ THÀNH CÔNG |
| 287 | `modules/maintenance_detail_tbl_road_sign_chitiet.json` | module | 16,257 | 16,257 | 16,257 | 0 | 0 | 16,257 | 0 | 3,863 | 4,208.4 | ✅ THÀNH CÔNG |
| 288 | `modules/maintenance_detail_tbl_road_sign_tonghop.json` | module | 16,257 | 16,257 | 16,257 | 0 | 0 | 16,257 | 0 | 1,265 | 12,851.4 | ✅ THÀNH CÔNG |
| 289 | `modules/maintenance_detail_tbl_segment_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 290 | `modules/maintenance_detail_tbl_segment_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 291 | `modules/maintenance_detail_tbl_slope_chitiet.json` | module | 10,700 | 10,700 | 10,700 | 0 | 0 | 10,700 | 0 | 1,351 | 7,920.1 | ✅ THÀNH CÔNG |
| 292 | `modules/maintenance_detail_tbl_slope_tonghop.json` | module | 10,700 | 10,700 | 10,700 | 0 | 0 | 10,700 | 0 | 1,759 | 6,083.0 | ✅ THÀNH CÔNG |
| 293 | `modules/maintenance_detail_tbl_spill_way_chitiet.json` | module | 119 | 119 | 119 | 0 | 0 | 119 | 0 | 27 | 4,407.4 | ✅ THÀNH CÔNG |
| 294 | `modules/maintenance_detail_tbl_spill_way_tonghop.json` | module | 119 | 119 | 119 | 0 | 0 | 119 | 0 | 36 | 3,305.6 | ✅ THÀNH CÔNG |
| 295 | `modules/maintenance_detail_tbl_street_lighting_chitiet.json` | module | 4,643 | 4,643 | 4,643 | 0 | 0 | 4,643 | 0 | 418 | 11,107.7 | ✅ THÀNH CÔNG |
| 296 | `modules/maintenance_detail_tbl_street_lighting_tonghop.json` | module | 4,643 | 4,643 | 4,643 | 0 | 0 | 4,643 | 0 | 353 | 13,153.0 | ✅ THÀNH CÔNG |
| 297 | `modules/maintenance_detail_tbl_toll_booth_chitiet.json` | module | 77 | 77 | 77 | 0 | 0 | 77 | 0 | 24 | 3,208.3 | ✅ THÀNH CÔNG |
| 298 | `modules/maintenance_detail_tbl_toll_booth_tonghop.json` | module | 77 | 77 | 77 | 0 | 0 | 77 | 0 | 54 | 1,425.9 | ✅ THÀNH CÔNG |
| 299 | `modules/maintenance_detail_tbl_traffic_accident_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 300 | `modules/maintenance_detail_tbl_traffic_accident_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 301 | `modules/maintenance_detail_tbl_transverse_drainage_chitiet.json` | module | 9,528 | 9,528 | 9,528 | 0 | 0 | 9,528 | 0 | 782 | 12,184.1 | ✅ THÀNH CÔNG |
| 302 | `modules/maintenance_detail_tbl_transverse_drainage_tonghop.json` | module | 4,544 | 4,544 | 4,544 | 0 | 0 | 4,544 | 0 | 804 | 5,651.7 | ✅ THÀNH CÔNG |
| 303 | `modules/maintenance_detail_tbl_tunnel_main_chitiet.json` | module | 13 | 13 | 13 | 0 | 0 | 13 | 0 | 23 | 565.2 | ✅ THÀNH CÔNG |
| 304 | `modules/maintenance_detail_tbl_tunnel_main_tonghop.json` | module | 13 | 13 | 13 | 0 | 0 | 13 | 0 | 24 | 541.7 | ✅ THÀNH CÔNG |
| 305 | `modules/maintenance_detail_tbl_underpass_box_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 306 | `modules/maintenance_detail_tbl_underpass_box_tonghop.json` | module | 787 | 787 | 787 | 0 | 0 | 787 | 0 | 73 | 10,780.8 | ✅ THÀNH CÔNG |
| 307 | `modules/maintenance_detail_tbl_unstr_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 23 | 0.0 | ✅ THÀNH CÔNG |
| 308 | `modules/maintenance_detail_tbl_unstr_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 309 | `modules/maintenance_detail_thongtindocdoc_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 310 | `modules/maintenance_detail_thongtindocdoc_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 311 | `modules/maintenance_detail_thongtinduanbot_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 312 | `modules/maintenance_detail_thongtinduanbot_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 313 | `modules/maintenance_detail_thongtinduongcong_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 314 | `modules/maintenance_detail_thongtinduongcong_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0.0 | ✅ THÀNH CÔNG |
| 315 | `modules/maintenance_detail_thongtinlandungkhancap_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 316 | `modules/maintenance_detail_thongtinlandungkhancap_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 317 | `modules/maintenance_detail_thongtintaitrong_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0.0 | ✅ THÀNH CÔNG |
| 318 | `modules/maintenance_detail_thongtintaitrong_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 319 | `modules/maintenance_detail_thongtintaitrongduong_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0.0 | ✅ THÀNH CÔNG |
| 320 | `modules/maintenance_detail_thongtintaitrongduong_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0.0 | ✅ THÀNH CÔNG |
| 321 | `modules/maintenance_detail_traffic_lights_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 322 | `modules/maintenance_detail_traffic_lights_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 34 | 0.0 | ✅ THÀNH CÔNG |
| 323 | `modules/maintenance_detail_tunnel_emergency_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 22 | 0.0 | ✅ THÀNH CÔNG |
| 324 | `modules/maintenance_detail_tunnel_emergency_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 17 | 0.0 | ✅ THÀNH CÔNG |
| 325 | `modules/maintenance_detail_tunnel_other_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0.0 | ✅ THÀNH CÔNG |
| 326 | `modules/maintenance_detail_tunnel_other_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 327 | `modules/maintenance_detail_tunnel_work_outside_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0.0 | ✅ THÀNH CÔNG |
| 328 | `modules/maintenance_detail_tunnel_work_outside_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0.0 | ✅ THÀNH CÔNG |
| 329 | `modules/maintenance_detail_weight_station_chitiet.json` | module | 19 | 19 | 19 | 0 | 0 | 19 | 0 | 20 | 950.0 | ✅ THÀNH CÔNG |
| 330 | `modules/maintenance_detail_weight_station_tonghop.json` | module | 19 | 19 | 19 | 0 | 0 | 19 | 0 | 22 | 863.6 | ✅ THÀNH CÔNG |
| 331 | `modules/maintenance_detail_yeucauphananh_chitiet.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0.0 | ✅ THÀNH CÔNG |
| 332 | `modules/maintenance_detail_yeucauphananh_tonghop.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0.0 | ✅ THÀNH CÔNG |
| 333 | `modules/maintenance_duonggom_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 334 | `modules/maintenance_duonggom_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 335 | `modules/maintenance_duongnhanh_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 336 | `modules/maintenance_duongnhanh_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 337 | `modules/maintenance_duongtranh_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 338 | `modules/maintenance_duongtranh_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 43 | 23.3 | ✅ THÀNH CÔNG |
| 339 | `modules/maintenance_gp_csdtttv_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 340 | `modules/maintenance_gp_csdtttv_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 341 | `modules/maintenance_gp_ctdkt_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 342 | `modules/maintenance_gp_ctdkt_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 26 | 38.5 | ✅ THÀNH CÔNG |
| 343 | `modules/maintenance_gp_ldvh_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 31 | 32.3 | ✅ THÀNH CÔNG |
| 344 | `modules/maintenance_gp_ldvh_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 345 | `modules/maintenance_gp_tcngdn_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 346 | `modules/maintenance_gp_tcngdn_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 26 | 38.5 | ✅ THÀNH CÔNG |
| 347 | `modules/maintenance_gp_tkngdn_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 348 | `modules/maintenance_gp_tkngdn_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 349 | `modules/maintenance_gp_ttv_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 350 | `modules/maintenance_gp_ttv_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 25 | 40.0 | ✅ THÀNH CÔNG |
| 351 | `modules/maintenance_mst_commune_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 352 | `modules/maintenance_mst_commune_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 30 | 33.3 | ✅ THÀNH CÔNG |
| 353 | `modules/maintenance_mst_counting_station_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 354 | `modules/maintenance_mst_counting_station_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 82 | 12.2 | ✅ THÀNH CÔNG |
| 355 | `modules/maintenance_mst_national_expressway_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 31 | 32.3 | ✅ THÀNH CÔNG |
| 356 | `modules/maintenance_mst_national_expressway_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 357 | `modules/maintenance_mst_national_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 358 | `modules/maintenance_mst_national_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 27 | 37.0 | ✅ THÀNH CÔNG |
| 359 | `modules/maintenance_mst_provincial_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 360 | `modules/maintenance_mst_provincial_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 27 | 37.0 | ✅ THÀNH CÔNG |
| 361 | `modules/maintenance_mst_special_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 362 | `modules/maintenance_mst_special_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 363 | `modules/maintenance_mst_urban_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 364 | `modules/maintenance_mst_urban_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 365 | `modules/maintenance_mst_village_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 27 | 37.0 | ✅ THÀNH CÔNG |
| 366 | `modules/maintenance_mst_village_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 367 | `modules/maintenance_rmdinventory_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 33 | 30.3 | ✅ THÀNH CÔNG |
| 368 | `modules/maintenance_rmdinventory_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 369 | `modules/maintenance_road_sphere_mirror_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 370 | `modules/maintenance_road_sphere_mirror_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 371 | `modules/maintenance_tbl_bridge_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 372 | `modules/maintenance_tbl_bridge_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 373 | `modules/maintenance_tbl_bus_station_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 374 | `modules/maintenance_tbl_bus_station_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 375 | `modules/maintenance_tbl_bus_stops_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 376 | `modules/maintenance_tbl_bus_stops_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 377 | `modules/maintenance_tbl_disaster_res_facility_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 378 | `modules/maintenance_tbl_disaster_res_facility_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 15 | 66.7 | ✅ THÀNH CÔNG |
| 379 | `modules/maintenance_tbl_ferry_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 380 | `modules/maintenance_tbl_ferry_extend_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 381 | `modules/maintenance_tbl_ferry_extend_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 28 | 35.7 | ✅ THÀNH CÔNG |
| 382 | `modules/maintenance_tbl_ferry_terminal_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 27 | 37.0 | ✅ THÀNH CÔNG |
| 383 | `modules/maintenance_tbl_ferry_terminal_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 43 | 23.3 | ✅ THÀNH CÔNG |
| 384 | `modules/maintenance_tbl_ferry_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 34 | 29.4 | ✅ THÀNH CÔNG |
| 385 | `modules/maintenance_tbl_first_aid_station_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 44 | 22.7 | ✅ THÀNH CÔNG |
| 386 | `modules/maintenance_tbl_first_aid_station_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 28 | 35.7 | ✅ THÀNH CÔNG |
| 387 | `modules/maintenance_tbl_guardrail_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 388 | `modules/maintenance_tbl_guardrail_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 389 | `modules/maintenance_tbl_guide_post_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 390 | `modules/maintenance_tbl_guide_post_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 391 | `modules/maintenance_tbl_infrastructure_row_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 27 | 37.0 | ✅ THÀNH CÔNG |
| 392 | `modules/maintenance_tbl_infrastructure_row_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 30 | 33.3 | ✅ THÀNH CÔNG |
| 393 | `modules/maintenance_tbl_intersection_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 394 | `modules/maintenance_tbl_intersection_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 395 | `modules/maintenance_tbl_its_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 61 | 16.4 | ✅ THÀNH CÔNG |
| 396 | `modules/maintenance_tbl_its_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 397 | `modules/maintenance_tbl_km_post_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 398 | `modules/maintenance_tbl_km_post_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 399 | `modules/maintenance_tbl_land_btra_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 400 | `modules/maintenance_tbl_land_btra_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 401 | `modules/maintenance_tbl_longitudinal_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 15 | 66.7 | ✅ THÀNH CÔNG |
| 402 | `modules/maintenance_tbl_longitudinal_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 403 | `modules/maintenance_tbl_median_strip_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 404 | `modules/maintenance_tbl_median_strip_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 405 | `modules/maintenance_tbl_noise_barrier_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 406 | `modules/maintenance_tbl_noise_barrier_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 407 | `modules/maintenance_tbl_overlaproad_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 15 | 66.7 | ✅ THÀNH CÔNG |
| 408 | `modules/maintenance_tbl_overlaproad_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 409 | `modules/maintenance_tbl_pierstr_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 410 | `modules/maintenance_tbl_pierstr_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 411 | `modules/maintenance_tbl_pontoon_bridge_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 412 | `modules/maintenance_tbl_pontoon_bridge_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 413 | `modules/maintenance_tbl_potential_black_spot_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 414 | `modules/maintenance_tbl_potential_black_spot_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 415 | `modules/maintenance_tbl_railway_crossing_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 416 | `modules/maintenance_tbl_railway_crossing_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 417 | `modules/maintenance_tbl_remediation_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 418 | `modules/maintenance_tbl_remediation_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 15 | 66.7 | ✅ THÀNH CÔNG |
| 419 | `modules/maintenance_tbl_rescue_vehicle_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 420 | `modules/maintenance_tbl_rescue_vehicle_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 44 | 22.7 | ✅ THÀNH CÔNG |
| 421 | `modules/maintenance_tbl_rest_stops_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 422 | `modules/maintenance_tbl_rest_stops_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 423 | `modules/maintenance_tbl_retaining_wall_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 424 | `modules/maintenance_tbl_retaining_wall_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 36 | 27.8 | ✅ THÀNH CÔNG |
| 425 | `modules/maintenance_tbl_rmd_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 426 | `modules/maintenance_tbl_rmd_foreign_road_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 427 | `modules/maintenance_tbl_rmd_foreign_road_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 25 | 40.0 | ✅ THÀNH CÔNG |
| 428 | `modules/maintenance_tbl_rmd_socio_economic_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 429 | `modules/maintenance_tbl_rmd_socio_economic_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 430 | `modules/maintenance_tbl_rmd_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 431 | `modules/maintenance_tbl_road_admin_office_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 32 | 31.3 | ✅ THÀNH CÔNG |
| 432 | `modules/maintenance_tbl_road_admin_office_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 433 | `modules/maintenance_tbl_road_sign_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 434 | `modules/maintenance_tbl_road_sign_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 435 | `modules/maintenance_tbl_segment_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 436 | `modules/maintenance_tbl_segment_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 16 | 62.5 | ✅ THÀNH CÔNG |
| 437 | `modules/maintenance_tbl_slope_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 438 | `modules/maintenance_tbl_slope_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 439 | `modules/maintenance_tbl_spill_way_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 440 | `modules/maintenance_tbl_spill_way_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 441 | `modules/maintenance_tbl_street_lighting_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 442 | `modules/maintenance_tbl_street_lighting_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 443 | `modules/maintenance_tbl_toll_booth_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 444 | `modules/maintenance_tbl_toll_booth_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 39 | 25.6 | ✅ THÀNH CÔNG |
| 445 | `modules/maintenance_tbl_traffic_accident_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 446 | `modules/maintenance_tbl_traffic_accident_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 447 | `modules/maintenance_tbl_transverse_drainage_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 448 | `modules/maintenance_tbl_transverse_drainage_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 449 | `modules/maintenance_tbl_tunnel_main_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 450 | `modules/maintenance_tbl_tunnel_main_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 451 | `modules/maintenance_tbl_underpass_box_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 29 | 34.5 | ✅ THÀNH CÔNG |
| 452 | `modules/maintenance_tbl_underpass_box_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 26 | 38.5 | ✅ THÀNH CÔNG |
| 453 | `modules/maintenance_tbl_unstr_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 454 | `modules/maintenance_tbl_unstr_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 40 | 25.0 | ✅ THÀNH CÔNG |
| 455 | `modules/maintenance_thongtindocdoc_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 37 | 27.0 | ✅ THÀNH CÔNG |
| 456 | `modules/maintenance_thongtindocdoc_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 457 | `modules/maintenance_thongtinduanbot_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 458 | `modules/maintenance_thongtinduanbot_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 459 | `modules/maintenance_thongtinduongcong_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 460 | `modules/maintenance_thongtinduongcong_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 461 | `modules/maintenance_thongtinlandungkhancap_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 462 | `modules/maintenance_thongtinlandungkhancap_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 463 | `modules/maintenance_thongtintaitrong_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 464 | `modules/maintenance_thongtintaitrong_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 23 | 43.5 | ✅ THÀNH CÔNG |
| 465 | `modules/maintenance_thongtintaitrongduong_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 466 | `modules/maintenance_thongtintaitrongduong_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 467 | `modules/maintenance_traffic_lights_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 468 | `modules/maintenance_traffic_lights_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 469 | `modules/maintenance_tunnel_emergency_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 470 | `modules/maintenance_tunnel_emergency_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 471 | `modules/maintenance_tunnel_other_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 17 | 58.8 | ✅ THÀNH CÔNG |
| 472 | `modules/maintenance_tunnel_other_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 22 | 45.5 | ✅ THÀNH CÔNG |
| 473 | `modules/maintenance_tunnel_work_outside_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 19 | 52.6 | ✅ THÀNH CÔNG |
| 474 | `modules/maintenance_tunnel_work_outside_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 18 | 55.6 | ✅ THÀNH CÔNG |
| 475 | `modules/maintenance_weight_station_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 31 | 32.3 | ✅ THÀNH CÔNG |
| 476 | `modules/maintenance_weight_station_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 24 | 41.7 | ✅ THÀNH CÔNG |
| 477 | `modules/maintenance_yeucauphananh_chitiet.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 21 | 47.6 | ✅ THÀNH CÔNG |
| 478 | `modules/maintenance_yeucauphananh_tonghop.json` | module | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 20 | 50.0 | ✅ THÀNH CÔNG |
| 479 | `modules/reference_moc_dbvn_c_c_ben_pha.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 21 | 95.2 | ✅ THÀNH CÔNG |
| 480 | `modules/reference_moc_dbvn_c_c_benxe_khach_bus.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 19 | 157.9 | ✅ THÀNH CÔNG |
| 481 | `modules/reference_moc_dbvn_c_c_bongden.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 22 | 136.4 | ✅ THÀNH CÔNG |
| 482 | `modules/reference_moc_dbvn_c_c_botriduoi.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 19 | 263.2 | ✅ THÀNH CÔNG |
| 483 | `modules/reference_moc_dbvn_c_c_capcongtrinh_benpha.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 49 | 81.6 | ✅ THÀNH CÔNG |
| 484 | `modules/reference_moc_dbvn_c_c_capcongtrinh_cauphao.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 485 | `modules/reference_moc_dbvn_c_c_capduong.json` | module | 13 | 13 | 13 | 0 | 0 | 13 | 0 | 23 | 565.2 | ✅ THÀNH CÔNG |
| 486 | `modules/reference_moc_dbvn_c_c_capnha.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 20 | 250.0 | ✅ THÀNH CÔNG |
| 487 | `modules/reference_moc_dbvn_c_c_chieuxechay.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 18 | 222.2 | ✅ THÀNH CÔNG |
| 488 | `modules/reference_moc_dbvn_c_c_chusohuu.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 489 | `modules/reference_moc_dbvn_c_c_congnuocngang.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 49 | 163.3 | ✅ THÀNH CÔNG |
| 490 | `modules/reference_moc_dbvn_c_c_congnuocngang_ketcauvan.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 27 | 148.1 | ✅ THÀNH CÔNG |
| 491 | `modules/reference_moc_dbvn_c_c_congthoatnuocngang.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 20 | 150.0 | ✅ THÀNH CÔNG |
| 492 | `modules/reference_moc_dbvn_c_c_congtrinhduongbo.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 493 | `modules/reference_moc_dbvn_c_c_coquanquanly_htchieusang.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 19 | 263.2 | ✅ THÀNH CÔNG |
| 494 | `modules/reference_moc_dbvn_c_c_daiphancach.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 20 | 450.0 | ✅ THÀNH CÔNG |
| 495 | `modules/reference_moc_dbvn_c_c_dautukhaithac.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 20 | 400.0 | ✅ THÀNH CÔNG |
| 496 | `modules/reference_moc_dbvn_c_c_diemgiaobangduongsat.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 16 | 125.0 | ✅ THÀNH CÔNG |
| 497 | `modules/reference_moc_dbvn_c_c_dieuuocquocte.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 19 | 105.3 | ✅ THÀNH CÔNG |
| 498 | `modules/reference_moc_dbvn_c_c_dinhdangbotro.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 23 | 2,565.2 | ✅ THÀNH CÔNG |
| 499 | `modules/reference_moc_dbvn_c_c_donviquanlysudung.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 20 | 200.0 | ✅ THÀNH CÔNG |
| 500 | `modules/reference_moc_dbvn_c_c_duongdoingoai.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 501 | `modules/reference_moc_dbvn_c_c_hinhdangbienbao.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 17 | 294.1 | ✅ THÀNH CÔNG |
| 502 | `modules/reference_moc_dbvn_c_c_hinhdangcong.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 19 | 315.8 | ✅ THÀNH CÔNG |
| 503 | `modules/reference_moc_dbvn_c_c_hinhdangmatcat_cotbienbaoduongbo.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 26 | 115.4 | ✅ THÀNH CÔNG |
| 504 | `modules/reference_moc_dbvn_c_c_hinhdangnutgiao.json` | module | 10 | 10 | 10 | 0 | 0 | 10 | 0 | 18 | 555.6 | ✅ THÀNH CÔNG |
| 505 | `modules/reference_moc_dbvn_c_c_hinhdangranh.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 506 | `modules/reference_moc_dbvn_c_c_hinhthuckhaithacthuadat.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 19 | 210.5 | ✅ THÀNH CÔNG |
| 507 | `modules/reference_moc_dbvn_c_c_ketcau_mongcoccaudanbenpha.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 20 | 150.0 | ✅ THÀNH CÔNG |
| 508 | `modules/reference_moc_dbvn_c_c_ketcau_motrucaudanbenpha.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 509 | `modules/reference_moc_dbvn_c_c_ketcau_nhipcodinhbenpha.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 23 | 173.9 | ✅ THÀNH CÔNG |
| 510 | `modules/reference_moc_dbvn_c_c_ketcau_nhipgatgubenpha.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 23 | 87.0 | ✅ THÀNH CÔNG |
| 511 | `modules/reference_moc_dbvn_c_c_ketcau_trutuacaudanbenpha.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 27 | 111.1 | ✅ THÀNH CÔNG |
| 512 | `modules/reference_moc_dbvn_c_c_ketcaubaove.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 29 | 137.9 | ✅ THÀNH CÔNG |
| 513 | `modules/reference_moc_dbvn_c_c_ketcaucong_ranh.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 24 | 250.0 | ✅ THÀNH CÔNG |
| 514 | `modules/reference_moc_dbvn_c_c_ketcaugiaovuot_duongsat.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 20 | 150.0 | ✅ THÀNH CÔNG |
| 515 | `modules/reference_moc_dbvn_c_c_ketcaumaicong.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 19 | 263.2 | ✅ THÀNH CÔNG |
| 516 | `modules/reference_moc_dbvn_c_c_ketcaunhachoxebus.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 19 | 210.5 | ✅ THÀNH CÔNG |
| 517 | `modules/reference_moc_dbvn_c_c_ketcausancong.json` | module | 12 | 12 | 12 | 0 | 0 | 12 | 0 | 22 | 545.5 | ✅ THÀNH CÔNG |
| 518 | `modules/reference_moc_dbvn_c_c_ketuongchan.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 18 | 500.0 | ✅ THÀNH CÔNG |
| 519 | `modules/reference_moc_dbvn_c_c_khudichvu_baidauxe.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 19 | 368.4 | ✅ THÀNH CÔNG |
| 520 | `modules/reference_moc_dbvn_c_c_kieubaovemaidoc.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 22 | 409.1 | ✅ THÀNH CÔNG |
| 521 | `modules/reference_moc_dbvn_c_c_loai_kieubenpha.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 20 | 350.0 | ✅ THÀNH CÔNG |
| 522 | `modules/reference_moc_dbvn_c_c_loaibaixe_tramnghi_khudichvu.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 39 | 76.9 | ✅ THÀNH CÔNG |
| 523 | `modules/reference_moc_dbvn_c_c_loaibenpha.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 17 | 117.6 | ✅ THÀNH CÔNG |
| 524 | `modules/reference_moc_dbvn_c_c_loaican.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 21 | 142.9 | ✅ THÀNH CÔNG |
| 525 | `modules/reference_moc_dbvn_c_c_loaicauphao.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 29 | 103.4 | ✅ THÀNH CÔNG |
| 526 | `modules/reference_moc_dbvn_c_c_loaicoctieu_coch.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 25 | 80.0 | ✅ THÀNH CÔNG |
| 527 | `modules/reference_moc_dbvn_c_c_loaicong.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 21 | 142.9 | ✅ THÀNH CÔNG |
| 528 | `modules/reference_moc_dbvn_c_c_loaicong_ranh.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 17 | 176.5 | ✅ THÀNH CÔNG |
| 529 | `modules/reference_moc_dbvn_c_c_loaicongdoc_ranhdoc.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 530 | `modules/reference_moc_dbvn_c_c_loaicongtrinh_tranngam.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 29 | 137.9 | ✅ THÀNH CÔNG |
| 531 | `modules/reference_moc_dbvn_c_c_loaicongtrinhtienich.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 30 | 266.7 | ✅ THÀNH CÔNG |
| 532 | `modules/reference_moc_dbvn_c_c_loaidiemdung.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 18 | 166.7 | ✅ THÀNH CÔNG |
| 533 | `modules/reference_moc_dbvn_c_c_loaiduong.json` | module | 14 | 14 | 14 | 0 | 0 | 14 | 0 | 19 | 736.8 | ✅ THÀNH CÔNG |
| 534 | `modules/reference_moc_dbvn_c_c_loaiduong_cau.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 29 | 275.9 | ✅ THÀNH CÔNG |
| 535 | `modules/reference_moc_dbvn_c_c_loaiduong_vitridatbien.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 43 | 93.0 | ✅ THÀNH CÔNG |
| 536 | `modules/reference_moc_dbvn_c_c_loaigiao.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 22 | 136.4 | ✅ THÀNH CÔNG |
| 537 | `modules/reference_moc_dbvn_c_c_loaiketcaumattran.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 20 | 250.0 | ✅ THÀNH CÔNG |
| 538 | `modules/reference_moc_dbvn_c_c_loaimatduong.json` | module | 19 | 19 | 19 | 0 | 0 | 19 | 0 | 24 | 791.7 | ✅ THÀNH CÔNG |
| 539 | `modules/reference_moc_dbvn_c_c_loaiphongve.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 30 | 166.7 | ✅ THÀNH CÔNG |
| 540 | `modules/reference_moc_dbvn_c_c_loaiphuongtien.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 19 | 368.4 | ✅ THÀNH CÔNG |
| 541 | `modules/reference_moc_dbvn_c_c_loaitaisan_bienbaoduongbo.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 26 | 307.7 | ✅ THÀNH CÔNG |
| 542 | `modules/reference_moc_dbvn_c_c_loaitaisanholan.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 25 | 80.0 | ✅ THÀNH CÔNG |
| 543 | `modules/reference_moc_dbvn_c_c_loaithicong.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 23 | 130.4 | ✅ THÀNH CÔNG |
| 544 | `modules/reference_moc_dbvn_c_c_loaitramcapcuu.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 18 | 166.7 | ✅ THÀNH CÔNG |
| 545 | `modules/reference_moc_dbvn_c_c_loaitrudo.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 29 | 137.9 | ✅ THÀNH CÔNG |
| 546 | `modules/reference_moc_dbvn_c_c_loaituongchan.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 547 | `modules/reference_moc_dbvn_c_c_mong.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 22 | 90.9 | ✅ THÀNH CÔNG |
| 548 | `modules/reference_moc_dbvn_c_c_mucdichlapdat.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 21 | 238.1 | ✅ THÀNH CÔNG |
| 549 | `modules/reference_moc_dbvn_c_c_nenduong.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 550 | `modules/reference_moc_dbvn_c_c_nhahat_truso.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 17 | 117.6 | ✅ THÀNH CÔNG |
| 551 | `modules/reference_moc_dbvn_c_c_nutgiao.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 22 | 318.2 | ✅ THÀNH CÔNG |
| 552 | `modules/reference_moc_dbvn_c_c_phanloaimaidoc.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 18 | 166.7 | ✅ THÀNH CÔNG |
| 553 | `modules/reference_moc_dbvn_c_c_phuongthucdaoham.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 16 | 312.5 | ✅ THÀNH CÔNG |
| 554 | `modules/reference_moc_dbvn_c_c_reissue_type.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 22 | 272.7 | ✅ THÀNH CÔNG |
| 555 | `modules/reference_moc_dbvn_c_c_rmd_diahinhduongdoingoai.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 24 | 166.7 | ✅ THÀNH CÔNG |
| 556 | `modules/reference_moc_dbvn_c_c_rmd_ketcauviahe.json` | module | 27 | 27 | 27 | 0 | 0 | 27 | 0 | 31 | 871.0 | ✅ THÀNH CÔNG |
| 557 | `modules/reference_moc_dbvn_c_c_rmd_loaiketcaulegiaco.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 26 | 346.2 | ✅ THÀNH CÔNG |
| 558 | `modules/reference_moc_dbvn_c_c_sohieubienbao.json` | module | 382 | 382 | 382 | 0 | 0 | 382 | 0 | 49 | 7,795.9 | ✅ THÀNH CÔNG |
| 559 | `modules/reference_moc_dbvn_c_c_tinhtranghoatdong.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 15 | 200.0 | ✅ THÀNH CÔNG |
| 560 | `modules/reference_moc_dbvn_c_c_tinhtrangthuadat.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 19 | 263.2 | ✅ THÀNH CÔNG |
| 561 | `modules/reference_moc_dbvn_c_c_tochucgiaothong_ham.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 21 | 95.2 | ✅ THÀNH CÔNG |
| 562 | `modules/reference_moc_dbvn_c_c_trambienap.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 21 | 95.2 | ✅ THÀNH CÔNG |
| 563 | `modules/reference_moc_dbvn_c_c_trangthaitrienkhai.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 19 | 157.9 | ✅ THÀNH CÔNG |
| 564 | `modules/reference_moc_dbvn_c_c_trungtamdieuhanh.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 19 | 105.3 | ✅ THÀNH CÔNG |
| 565 | `modules/reference_moc_dbvn_c_c_tuongchongon.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 18 | 277.8 | ✅ THÀNH CÔNG |
| 566 | `modules/reference_moc_dbvn_c_c_tuyendoingoai.json` | module | 10 | 10 | 10 | 0 | 0 | 10 | 0 | 28 | 357.1 | ✅ THÀNH CÔNG |
| 567 | `modules/reference_moc_dbvn_c_c_tuyenduongnhanh_en.json` | module | 580 | 580 | 580 | 0 | 0 | 580 | 0 | 77 | 7,532.5 | ✅ THÀNH CÔNG |
| 568 | `modules/reference_moc_dbvn_c_c_tuyenduongnhanh_vi.json` | module | 580 | 580 | 580 | 0 | 0 | 580 | 0 | 66 | 8,787.9 | ✅ THÀNH CÔNG |
| 569 | `modules/reference_moc_dbvn_c_c_tuyenduongquocgia.json` | module | 204 | 204 | 204 | 0 | 0 | 204 | 0 | 43 | 4,744.2 | ✅ THÀNH CÔNG |
| 570 | `modules/reference_moc_dbvn_c_c_vatlieu_bienbaoduongbo.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 21 | 190.5 | ✅ THÀNH CÔNG |
| 571 | `modules/reference_moc_dbvn_c_c_vatlieu_cotbienbaoguongcau.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 572 | `modules/reference_moc_dbvn_c_c_vatlieucoctieu.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 22 | 227.3 | ✅ THÀNH CÔNG |
| 573 | `modules/reference_moc_dbvn_c_c_vatlieucong_hotu.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 18 | 388.9 | ✅ THÀNH CÔNG |
| 574 | `modules/reference_moc_dbvn_c_c_vatlieucotkm.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 25 | 200.0 | ✅ THÀNH CÔNG |
| 575 | `modules/reference_moc_dbvn_c_c_vatlieuhoga_thu_tham.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 16 | 250.0 | ✅ THÀNH CÔNG |
| 576 | `modules/reference_moc_dbvn_c_c_vatlieuholan.json` | module | 10 | 10 | 10 | 0 | 0 | 10 | 0 | 19 | 526.3 | ✅ THÀNH CÔNG |
| 577 | `modules/reference_moc_dbvn_c_c_vatlieuraochan.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 17 | 411.8 | ✅ THÀNH CÔNG |
| 578 | `modules/reference_moc_dbvn_c_c_vatlieutuongcanh.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 21 | 238.1 | ✅ THÀNH CÔNG |
| 579 | `modules/reference_moc_dbvn_c_c_vatlieutuongke.json` | module | 10 | 10 | 10 | 0 | 0 | 10 | 0 | 25 | 400.0 | ✅ THÀNH CÔNG |
| 580 | `modules/reference_moc_dbvn_c_c_vitri_benxebuskhach.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 16 | 250.0 | ✅ THÀNH CÔNG |
| 581 | `modules/reference_moc_dbvn_c_c_vitri_htchieusang.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 18 | 388.9 | ✅ THÀNH CÔNG |
| 582 | `modules/reference_moc_dbvn_c_c_vitri_khobaivattuduphong.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 583 | `modules/reference_moc_dbvn_c_c_vitri_nhahatquanlyduongbo.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 21 | 190.5 | ✅ THÀNH CÔNG |
| 584 | `modules/reference_moc_dbvn_c_c_vitri_raochongon.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 17 | 176.5 | ✅ THÀNH CÔNG |
| 585 | `modules/reference_moc_dbvn_c_c_vitricongdoc_ranh.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 19 | 368.4 | ✅ THÀNH CÔNG |
| 586 | `modules/reference_moc_dbvn_c_c_vitricotthuytri.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 22 | 181.8 | ✅ THÀNH CÔNG |
| 587 | `modules/reference_moc_dbvn_c_c_vitridaiphancach.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 17 | 176.5 | ✅ THÀNH CÔNG |
| 588 | `modules/reference_moc_dbvn_c_c_vitridiemdungbus_khach.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 19 | 210.5 | ✅ THÀNH CÔNG |
| 589 | `modules/reference_moc_dbvn_c_c_vitriholan_tonsong.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 17 | 411.8 | ✅ THÀNH CÔNG |
| 590 | `modules/reference_moc_dbvn_c_c_vitrilapdat_coctieuh.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 18 | 388.9 | ✅ THÀNH CÔNG |
| 591 | `modules/reference_moc_dbvn_c_c_vitrimaidoc.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 25 | 160.0 | ✅ THÀNH CÔNG |
| 592 | `modules/reference_moc_dbvn_c_c_vitrinhadieuhanh.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 24 | 166.7 | ✅ THÀNH CÔNG |
| 593 | `modules/reference_moc_dbvn_c_c_vitritramdungnghi_khudichvu.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 19 | 210.5 | ✅ THÀNH CÔNG |
| 594 | `modules/reference_moc_dbvn_c_c_vitritramkiemsoat_trongtai.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 16 | 187.5 | ✅ THÀNH CÔNG |
| 595 | `modules/reference_moc_dbvn_c_c_xeploaibai.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 25 | 160.0 | ✅ THÀNH CÔNG |
| 596 | `modules/reference_moc_dbvn_c_dm_banmatcau.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 28 | 178.6 | ✅ THÀNH CÔNG |
| 597 | `modules/reference_moc_dbvn_c_dm_capcongtrinhcau.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 20 | 400.0 | ✅ THÀNH CÔNG |
| 598 | `modules/reference_moc_dbvn_c_dm_chaychungvoi.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 19 | 157.9 | ✅ THÀNH CÔNG |
| 599 | `modules/reference_moc_dbvn_c_dm_chieuxechaydoantuyen.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 20 | 100.0 | ✅ THÀNH CÔNG |
| 600 | `modules/reference_moc_dbvn_c_dm_daiphancachgiuacau.json` | module | 4 | 4 | 4 | 0 | 0 | 4 | 0 | 17 | 235.3 | ✅ THÀNH CÔNG |
| 601 | `modules/reference_moc_dbvn_c_dm_dangdamchu.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 22 | 363.6 | ✅ THÀNH CÔNG |
| 602 | `modules/reference_moc_dbvn_c_dm_dangdamngang.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 18 | 277.8 | ✅ THÀNH CÔNG |
| 603 | `modules/reference_moc_dbvn_c_dm_dangketcau.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 18 | 277.8 | ✅ THÀNH CÔNG |
| 604 | `modules/reference_moc_dbvn_c_dm_dangmong.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 19 | 263.2 | ✅ THÀNH CÔNG |
| 605 | `modules/reference_moc_dbvn_c_dm_dangsodonhip.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 20 | 400.0 | ✅ THÀNH CÔNG |
| 606 | `modules/reference_moc_dbvn_c_dm_dangthanmo.json` | module | 17 | 17 | 17 | 0 | 0 | 17 | 0 | 18 | 944.4 | ✅ THÀNH CÔNG |
| 607 | `modules/reference_moc_dbvn_c_dm_doituongvuot.json` | module | 13 | 13 | 13 | 0 | 0 | 13 | 0 | 18 | 722.2 | ✅ THÀNH CÔNG |
| 608 | `modules/reference_moc_dbvn_c_dm_loaibien.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 28 | 321.4 | ✅ THÀNH CÔNG |
| 609 | `modules/reference_moc_dbvn_c_dm_loaigoicau.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 17 | 352.9 | ✅ THÀNH CÔNG |
| 610 | `modules/reference_moc_dbvn_c_dm_loaiketcau.json` | module | 72 | 72 | 72 | 0 | 0 | 72 | 0 | 23 | 3,130.4 | ✅ THÀNH CÔNG |
| 611 | `modules/reference_moc_dbvn_c_dm_loaiketcauduoi.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 23 | 87.0 | ✅ THÀNH CÔNG |
| 612 | `modules/reference_moc_dbvn_c_dm_loaikhecogian.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 20 | 250.0 | ✅ THÀNH CÔNG |
| 613 | `modules/reference_moc_dbvn_c_dm_loaixe.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 23 | 130.4 | ✅ THÀNH CÔNG |
| 614 | `modules/reference_moc_dbvn_c_dm_lopphumatcau.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 21 | 142.9 | ✅ THÀNH CÔNG |
| 615 | `modules/reference_moc_dbvn_c_dm_phanloaibenxe.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 27 | 222.2 | ✅ THÀNH CÔNG |
| 616 | `modules/reference_moc_dbvn_c_dm_trangthaicau.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 16 | 312.5 | ✅ THÀNH CÔNG |
| 617 | `modules/reference_moc_dbvn_c_dm_truchongvaxo.json` | module | 9 | 9 | 9 | 0 | 0 | 9 | 0 | 17 | 529.4 | ✅ THÀNH CÔNG |
| 618 | `modules/reference_moc_dbvn_c_dm_tunon.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 18 | 444.4 | ✅ THÀNH CÔNG |
| 619 | `modules/reference_moc_dbvn_c_dm_vatlieuketcauchiuluc.json` | module | 8 | 8 | 8 | 0 | 0 | 8 | 0 | 22 | 363.6 | ✅ THÀNH CÔNG |
| 620 | `modules/reference_moc_dbvn_c_dm_vatlieumong.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 39 | 128.2 | ✅ THÀNH CÔNG |
| 621 | `modules/reference_moc_dbvn_c_dm_vatlieuthan.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 17 | 294.1 | ✅ THÀNH CÔNG |
| 622 | `modules/reference_moc_dbvn_c_dm_xamu.json` | module | 5 | 5 | 5 | 0 | 0 | 5 | 0 | 16 | 312.5 | ✅ THÀNH CÔNG |
| 623 | `modules/reference_moc_dbvn_c_dm_yeucauthongthuyen.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 19 | 105.3 | ✅ THÀNH CÔNG |
| 624 | `modules/reference_moc_dbvn_c_loaitietdienham.json` | module | 2 | 2 | 2 | 0 | 0 | 2 | 0 | 16 | 125.0 | ✅ THÀNH CÔNG |
| 625 | `modules/reference_moc_dbvn_c_mst_road_sign_group.json` | module | 6 | 6 | 6 | 0 | 0 | 6 | 0 | 21 | 285.7 | ✅ THÀNH CÔNG |
| 626 | `modules/reference_moc_dbvn_c_mst_road_sign_location.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 20 | 350.0 | ✅ THÀNH CÔNG |
| 627 | `modules/reference_moc_dbvn_c_phuongthucthonggio.json` | module | 3 | 3 | 3 | 0 | 0 | 3 | 0 | 17 | 176.5 | ✅ THÀNH CÔNG |
| 628 | `modules/reference_moc_dbvn_c_tinhthanhpho.json` | module | 34 | 34 | 34 | 0 | 0 | 34 | 0 | 20 | 1,700.0 | ✅ THÀNH CÔNG |
| 629 | `modules/reference_moc_dbvn_c_xaphuong.json` | module | 3,322 | 3,322 | 3,322 | 0 | 0 | 3,322 | 0 | 329 | 10,097.3 | ✅ THÀNH CÔNG |
| 630 | `modules/reference_moc_dbvn_c_yeucauphananh.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0.0 | ✅ THÀNH CÔNG |
| 631 | `modules/road_length_2016.json` | module | 75 | 75 | 75 | 0 | 0 | 75 | 0 | 25 | 3,000.0 | ✅ THÀNH CÔNG |
| 632 | `modules/road_length_2017.json` | module | 75 | 75 | 75 | 0 | 0 | 75 | 0 | 24 | 3,125.0 | ✅ THÀNH CÔNG |
| 633 | `modules/road_length_2018.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 26 | 2,923.1 | ✅ THÀNH CÔNG |
| 634 | `modules/road_length_2019.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 29 | 2,620.7 | ✅ THÀNH CÔNG |
| 635 | `modules/road_length_2020.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 21 | 3,619.0 | ✅ THÀNH CÔNG |
| 636 | `modules/road_length_2021.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 24 | 3,166.7 | ✅ THÀNH CÔNG |
| 637 | `modules/road_length_2022.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 29 | 2,620.7 | ✅ THÀNH CÔNG |
| 638 | `modules/road_length_2023.json` | module | 76 | 76 | 76 | 0 | 0 | 76 | 0 | 27 | 2,814.8 | ✅ THÀNH CÔNG |
| 639 | `modules/road_length_2024.json` | module | 77 | 77 | 77 | 0 | 0 | 77 | 0 | 24 | 3,208.3 | ✅ THÀNH CÔNG |
| 640 | `modules/road_length_2025.json` | module | 78 | 78 | 78 | 0 | 0 | 78 | 0 | 23 | 3,391.3 | ✅ THÀNH CÔNG |
| 641 | `modules/road_length_2026.json` | module | 78 | 78 | 78 | 0 | 0 | 78 | 0 | 23 | 3,391.3 | ✅ THÀNH CÔNG |
| 642 | `modules/road_length_all.json` | module | 101 | 101 | 101 | 0 | 0 | 101 | 0 | 30 | 3,366.7 | ✅ THÀNH CÔNG |
| 643 | `modules/road_length_province_2016.json` | module | 58 | 58 | 58 | 0 | 0 | 58 | 0 | 28 | 2,071.4 | ✅ THÀNH CÔNG |
| 644 | `modules/road_length_province_2017.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 25 | 2,360.0 | ✅ THÀNH CÔNG |
| 645 | `modules/road_length_province_2018.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 24 | 2,458.3 | ✅ THÀNH CÔNG |
| 646 | `modules/road_length_province_2019.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 26 | 2,269.2 | ✅ THÀNH CÔNG |
| 647 | `modules/road_length_province_2020.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 35 | 1,685.7 | ✅ THÀNH CÔNG |
| 648 | `modules/road_length_province_2021.json` | module | 59 | 59 | 59 | 0 | 0 | 59 | 0 | 30 | 1,966.7 | ✅ THÀNH CÔNG |
| 649 | `modules/road_length_province_2022.json` | module | 60 | 60 | 60 | 0 | 0 | 60 | 0 | 24 | 2,500.0 | ✅ THÀNH CÔNG |
| 650 | `modules/road_length_province_2023.json` | module | 60 | 60 | 60 | 0 | 0 | 60 | 0 | 36 | 1,666.7 | ✅ THÀNH CÔNG |
| 651 | `modules/road_length_province_2024.json` | module | 60 | 60 | 60 | 0 | 0 | 60 | 0 | 26 | 2,307.7 | ✅ THÀNH CÔNG |
| 652 | `modules/road_length_province_2025.json` | module | 60 | 60 | 60 | 0 | 0 | 60 | 0 | 34 | 1,764.7 | ✅ THÀNH CÔNG |
| 653 | `modules/road_length_province_2026.json` | module | 60 | 60 | 60 | 0 | 0 | 60 | 0 | 27 | 2,222.2 | ✅ THÀNH CÔNG |
| 654 | `modules/road_length_province_all.json` | module | 63 | 63 | 63 | 0 | 0 | 63 | 0 | 27 | 2,333.3 | ✅ THÀNH CÔNG |
| 655 | `modules/road_sign_catalog.json` | module | 381 | 381 | 381 | 0 | 0 | 381 | 0 | 59 | 6,457.6 | ✅ THÀNH CÔNG |
| 656 | `modules/statistic_remediation-black-spot.json` | module | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0.0 | ✅ THÀNH CÔNG |
| 657 | `modules/statistic_road-sign.json` | module | 7 | 7 | 7 | 0 | 0 | 7 | 0 | 22 | 318.2 | ✅ THÀNH CÔNG |
| 658 | `modules/user_guide_tables.json` | module | 1,620 | 1,620 | 56 | 0 | 0 | 56 | 0 | 114 | 14,210.5 | ✅ THÀNH CÔNG |