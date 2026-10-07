# BÁO CÁO NGHIỆM THU TÍCH HỢP VÀ ĐỐI SOÁT DỮ LIỆU ĐÃ NẠP (STAGE 3 VERIFICATION REPORT)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**
**Giai đoạn 3: Smoke Test Read-Only đối soát dữ liệu 3 Dataset tiêu biểu**

---

## 1. TỔNG QUAN VÀ MỤC TIÊU NGHIỆM THU

### 1.1. Phạm vi thực hiện
Theo kế hoạch kiến trúc, **Giai đoạn 3** thực thi tích hợp tầng truy cập dữ liệu ứng dụng (`AssetQueryAdapter`, `DatasetApiController`) với cơ sở dữ liệu đã nạp thực tế (1,102,397 bản ghi trong PostgreSQL 16 + PostGIS) và tiến hành **Smoke Test hoàn toàn ở chế độ Read-Only** trên 3 tập dữ liệu đại diện:

1. **`mst_national_road` (Quy mô nhỏ - Dữ liệu danh mục tuyến đường):** 169 bản ghi, 77 trường thuộc tính.
2. **`tbl_bridge` (Quy mô vừa - Tài sản công trình không gian GIS):** 11,631 bản ghi, 131 trường thuộc tính, có tọa độ PostGIS Point (WGS 84).
3. **`tbl_road_sign` (Quy mô lớn - Khối lượng bản ghi lớn nhất hệ thống):** 222,112 bản ghi, 75 trường thuộc tính.

### 1.2. Cam kết và Nguyên tắc Tuân thủ
- **Tuyệt đối Read-Only:** Không sửa đổi, không xóa, không ghi đè bất kỳ bản ghi thô nào trong `raw_dataset_record`.
- **Bảo toàn CSDL:** Không chạy lại import toàn bộ, không tạo lại database, migration hay importer thứ hai.
- **Bảo mật thông tin:** Không ghi nhận mật khẩu, token, cookie hoặc secret vào log kiểm thử.
- **Chuẩn hóa Adapter:** Mọi truy vấn từ `DatasetApiController` đi qua `AssetQueryAdapter` theo đúng quy ước trong [`DATABASE_CODE_HANDOFF.md`](file:///c:/Demo_CucDuongBo/docs/database/DATABASE_CODE_HANDOFF.md).

---

## 2. KẾT QUẢ ĐỐI SOÁT CHI TIẾT 3 DATASET ĐẠI DIỆN

| Tiêu chí kiểm tra | `mst_national_road` | `tbl_bridge` | `tbl_road_sign` | Đánh giá & Kết luận |
| :--- | :---: | :---: | :---: | :--- |
| **Kỳ vọng manifest.json** | 169 | 11,631 | 222,112 | Khớp 100% manifest |
| **Báo cáo nạp trước (Job 11)** | 169 | 11,631 | 222,112 | Khớp báo cáo đối soát |
| **Số lượng thực tế trong CSDL** | **169** | **11,631** | **222,112** | Đếm chính xác qua SQL |
| **Tổng số trường (`fields`)** | **77 trường** | **131 trường** | **75 trường** | Khớp từ điển `dataset_field` |
| **Phân loại dữ liệu (`kind`)** | `asset` | `asset` | `asset` | Đúng danh mục quản lý |
| **Kiểu hình học (`geometryType`)**| `NONE` | `POINT` (WGS 84) | `NONE` (Placeholder) | Nhận diện đúng kiểu GIS |
| **Số geometry hợp lệ trong DB** | 0 | **7,436 điểm** | 0 | 4,195 cầu còn lại tọa độ null nguồn |
| **Thử nghiệm phân trang (Page)** | Trang 0 (10 bản ghi)<br>TotalPages = 17 | Trang 0 (20 bản ghi)<br>TotalPages = 582 | Trang 0 (25 bản ghi)<br>TotalPages = 8,885 | Phân trang server-side hoạt động chính xác |
| **Tra cứu trang sâu (Deep Page)** | Đạt | Đạt | Trang 100 (10 bản ghi)<br>Thời gian phản hồi < 50ms | B-Tree index hoạt động tối ưu |
| **Chi tiết bản ghi (Detail)** | Đọc bản ghi QL.1 (`17615867416863577`) | Đọc bản ghi cầu kèm lý trình | Đọc bản ghi biển báo kèm payload | Payload JSON nguyên bản hiển thị đầy đủ |
| **Dữ liệu không gian (BBOX)** | Không áp dụng | Trả về GeoJSON RFC 7946 FeatureCollection | Không áp dụng | Tọa độ `[lon, lat]` nằm trọn trong dải VN |

---

## 3. KẾT QUẢ KIỂM THỬ KHÔNG GIAN POSTGIS VÀ WEBGIS GEOJSON

Đối với tập dữ liệu **`tbl_bridge`**:
1. **Truy vấn Bounding Box toàn quốc:**
   - Endpoint: `GET /api/datasets/tbl_bridge/geo?bbox=102.0,8.0,110.0,24.0&limit=50`
   - Cấu trúc trả về:
     ```json
     {
       "type": "FeatureCollection",
       "totalFeatures": 50,
       "features": [
         {
           "type": "Feature",
           "id": "17786806577720671",
           "geometry": {
             "type": "Point",
             "coordinates": [106.758412, 21.942315]
           },
           "properties": {
             "id": "17786806577720671",
             "fielddisplay": "Cầu Kỳ Cùng",
             "dataset_key": "tbl_bridge",
             "x_min": 106.758412,
             "y_min": 21.942315
           }
         }
       ]
     }
     ```
2. **Quy chuẩn Tọa độ:**
   - Toàn bộ kinh độ $X$ kiểm tra đều nằm trong dải: $102.0^\circ \le X \le 110.0^\circ$ (Đông).
   - Toàn bộ vĩ độ $Y$ kiểm tra đều nằm trong dải: $8.0^\circ \le Y \le 24.0^\circ$ (Bắc).
   - Thứ tự tọa độ tuân thủ nghiêm ngặt chuẩn **RFC 7946 GeoJSON**: `[Kinh độ (Longitude), Vĩ độ (Latitude)]`.

---

## 4. ĐỐI SOÁT TÍNH BẤT BIẾN CỦA DỮ LIỆU (DATA INVARIANCE VERIFICATION)

Để đảm bảo quy tắc không làm thay đổi hay thất thoát dữ liệu, hệ thống đã thực hiện kiểm tra đối soát số dòng trước và sau khi toàn bộ bài kiểm thử khói được thực thi:

```sql
SELECT 
    (SELECT count(*) FROM raw_dataset_record) AS total_raw_records,
    (SELECT count(*) FROM dataset_registry) AS total_datasets,
    (SELECT count(*) FROM dataset_field) AS total_fields,
    (SELECT count(*) FROM import_job) AS total_jobs;
```

**Bảng kết quả đối soát:**
| Bảng CSDL | Trước Smoke Test | Sau Smoke Test | Chênh lệch | Kết luận |
| :--- | :---: | :---: | :---: | :--- |
| `raw_dataset_record` | **1,102,397** | **1,102,397** | **0** | Tuyệt đối bảo toàn dữ liệu gốc |
| `dataset_registry` | **658** | **658** | **0** | Metadata catalog không đổi |
| `dataset_field` | **10,142** | **10,142** | **0** | Từ điển trường không đổi |
| `import_job` | **1** | **1** | **0** | Không phát sinh phiên nạp thừa |

---

## 5. TỔNG HỢP KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST SUITE)

Hệ thống đã bổ sung bộ kiểm thử chuyên trách:
[`src/test/java/vn/gov/drvn/kcht/controller/DatasetImportSmokeIntegrationTest.java`](file:///c:/Demo_CucDuongBo/src/test/java/vn/gov/drvn/kcht/controller/DatasetImportSmokeIntegrationTest.java)

Lệnh thực thi toàn bộ test suite:
```powershell
.\mvnw.bat test
```
**Kết quả kiểm thử:**
```text
[INFO] Results:
[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  31.697 s
```
- **Tổng số test cases:** **40 / 40 test cases** thành công 100%.
- Không có lỗi timeout, không có HTTP request bị treo giữ lâu.
- Cơ chế ủy quyền từ `DatasetApiController` sang `AssetQueryAdapter` vận hành hoàn hảo với độ trễ phản hồi thấp.

---

## 6. KẾT LUẬN VÀ BÀN GIAO SANG GIAI ĐOẠN TIẾP THEO

- **Giai đoạn 3 đã hoàn tất xuất sắc:** Ứng dụng đọc được dữ liệu đã nạp mà không tạo bảng, importer hay schema mới; cả 3 dataset mẫu (`mst_national_road`, `tbl_bridge`, `tbl_road_sign`) đều trả về đúng dữ liệu theo báo cáo đối soát; tính năng phân trang và hình học PostGIS hoạt động ổn định.
- **Sẵn sàng chuyển sang Giai đoạn 4:** Kiểm tra và hoàn thiện backend API nền tảng theo hợp đồng đã chốt.
