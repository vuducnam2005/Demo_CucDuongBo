# PHÂN LOẠI TOÀN BỘ 658 TẬP DỮ LIỆU (DATASET CLASSIFICATION)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Tổng quan Phân loại 658 Tập Dữ liệu

Toàn bộ 658 file JSON trích xuất từ hệ thống cũ (`manifest.json`) được phân chia thành 5 phân tầng kiến trúc rõ ràng, phục vụ quá trình chuyển nạp và thiết kế schema:

| Phân tầng Kiến trúc | Bản chất Nghiệp vụ | Số file JSON | Số bản ghi | Tỷ lệ dữ liệu | Chiến lược Xử lý Schema |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1: Tài sản Hạ tầng (Assets)** | Đối tượng công trình vật lý, định vị trên tuyến | **57** | **833,083** | **75.46%** | Hợp nhất vào `asset_record` + `asset_geometry`. |
| **Tier 2: Danh mục Tham chiếu (Reference)** | Bảng tra mã, tên, phân cấp, biểu tượng | **152** | **5,992** | **0.54%** | Hợp nhất vào `reference_catalog`. |
| **Tier 3: Hồ sơ Tài liệu (Documents)** | Cây thư mục, metadata file đính kèm | **123** | **1,005** | **0.09%** | Nạp vào `document_folder` & `document_metadata`. |
| **Tier 4: Báo cáo & Thống kê (Reports/Stats)** | Dữ liệu snapshot báo cáo định kỳ theo năm | **314** | **263,991** | **23.91%** | Lưu ở Raw Ingestion; thay thế tầng Curated bằng Dynamic SQL View. |
| **Tier 5: Dữ liệu Ngoại vi / Không nạp vận hành** | Bảng trợ giúp, cẩm nang, các dataset rỗng | **12** | **1,620** | **0.15%** | Giữ ở Raw Ingestion; không nạp vào bảng Curated nghiệp vụ. |
| **TỔNG CỘNG** | | **658** | **1,104,088** | **100%** | |

---

## 2. Chi tiết Tier 1: 57 Tập Dữ liệu Tài sản Vật lý (Assets)

57 tập dữ liệu tài sản được chia thành 6 phân nhóm chuyên môn sâu:

### 2.1 Tuyến, Đoạn tuyến và Mạng lưới Đường bộ (11 datasets - 85,251 bản ghi)
Đây là khung xương sống (Spatial Backbone) định vị toàn bộ các tài sản khác thông qua lý trình (`km_from`, `km_to`):
1. `tbl_segment` (655 bản ghi): Thông tin tuyến đường bộ.
2. `tbl_rmd` (6,708 bản ghi): Phân đoạn quản lý tuyến đường (Road Management Division segment) - chứa 82 thuộc tính kỹ thuật.
3. `mst_national_road` (169 bản ghi): Danh mục các tuyến quốc lộ chính yếu.
4. `mst_national_expressway` (51 bản ghi): Hệ thống đường cao tốc quốc gia.
5. `mst_provincial_road` (1,425 bản ghi): Mạng lưới đường tỉnh.
6. `mst_urban_road` (20,120 bản ghi): Tuyến đường đô thị.
7. `mst_commune_road` (14,394 bản ghi): Tuyến đường xã.
8. `mst_village_road` (28,597 bản ghi): Đường thôn bản.
9. `mst_special_road` (175 bản ghi): Đường chuyên dùng.
10. `duongtranh` (67 bản ghi): Tuyến tránh đô thị.
11. `duongnhanh` (360 bản ghi): Đường nhánh kết nối nút giao.
12. `duonggom` (63 bản ghi): Đường gom song hành cao tốc.
13. `tbl_overlaproad` (3 bản ghi): Đoạn đi trùng giữa các quốc lộ.
14. `tbl_rmd_foreign_road` (10 bản ghi): Tuyến đường kết nối đối ngoại quốc tế.

### 2.2 Công trình Cầu và Kết cấu Vượt sông (7 datasets - 48,828 bản ghi)
1. `tbl_bridge` (11,631 bản ghi): Cầu quốc lộ (chứa 69 trường nghiệp vụ, bao gồm tải trọng, sơ đồ nhịp, tọa độ X/Y).
2. `tbl_pierstr` (14,948 bản ghi): Kết cấu nhịp cầu (liên kết cha qua `parent_id = 'bridge_<id>_tbl_pierstr'`).
3. `tbl_unstr` (21,463 bản ghi): Kết cấu mố trụ cầu (liên kết cha qua `parent_id = 'bridge_<id>_tbl_unstr'`).
4. `tbl_pontoon_bridge` (2 bản ghi): Cầu phao dã chiến.
5. `tbl_spill_way` (128 bản ghi): Đường tràn, cầu tràn, ngầm tràn mùa lũ.
6. `thongtintaitrong` (696 bản ghi): Thông tin kiểm định tải trọng cầu đường bộ.
7. `tbl_underpass_box` (808 bản ghi): Cống chui dân sinh, hầm chui giao thông.

### 2.3 An toàn Giao thông và Báo hiệu Đường bộ (6 datasets - 524,689 bản ghi)
Đây là nhóm có khối lượng bản ghi lớn nhất hệ thống, yêu cầu tối ưu hóa truy vấn đặc biệt:
1. `tbl_road_sign` (222,112 bản ghi): Biển báo giao thông đường bộ (biển cấm, biển chỉ dẫn, biển nguy hiểm).
2. `road_sphere_mirror` (191,928 bản ghi): Gương cầu lồi, cột cần vươn, giá long môn.
3. `tbl_guardrail` (51,697 bản ghi): Hộ lan mềm tôn lượn sóng, hàng rào bảo vệ taluy.
4. `tbl_guide_post` (37,042 bản ghi): Cọc tiêu phản quang, cọc H.
5. `tbl_km_post` (21,910 bản ghi): Cột mốc Km báo lý trình.
6. `tbl_noise_barrier` (24 bản ghi): Tường giảm âm, vách chắn tiếng ồn đô thị.

### 2.4 Hệ thống Thoát nước và Địa kỹ thuật (5 datasets - 149,792 bản ghi)
1. `tbl_longitudinal` (61,161 bản ghi): Cống dọc, rãnh biên thoát nước, hố ga, hào kỹ thuật dọc.
2. `tbl_transverse_drainage` (60,716 bản ghi): Cống thoát nước ngang (cống tròn, cống bản, cống hộp).
3. `tbl_slope` (11,099 bản ghi): Mái dốc gia cố taluy âm / taluy dương.
4. `tbl_retaining_wall` (9,856 bản ghi): Kè bê tông, kè đá rọ, tường chắn đất bảo vệ nền đường.
5. `tbl_median_strip` (6,960 bản ghi): Dải phân cách giữa phân chia chiều xe chạy.

### 2.5 Nút giao, Trạm và Cơ sở Phục vụ Vận tải (10 datasets - 13,874 bản ghi)
1. `tbl_intersection` (7,053 bản ghi): Nút giao cùng mức và khác mức.
2. `tbl_bus_stops` (5,418 bản ghi): Điểm đón trả khách, điểm dừng xe bus.
3. `tbl_street_lighting` (4,948 bản ghi): Hệ thống chiếu sáng công cộng đường bộ.
4. `tbl_bus_station` (387 bản ghi): Bến xe khách liên tỉnh, bến xe tĩnh.
5. `mst_counting_station` (372 bản ghi): Trạm đếm lưu lượng phương tiện.
6. `tbl_road_admin_office` (372 bản ghi): Hạt quản lý đường bộ, Đội bảo trì, Văn phòng Chi cục QLĐB.
7. `tbl_first_aid_station` (235 bản ghi): Trạm cứu nạn y tế đường bộ.
8. `tbl_toll_booth` (97 bản ghi): Trạm thu phí BOT / ETC.
9. `tbl_railway_crossing` (74 bản ghi): Điểm giao cắt đồng mức với đường sắt.
10. `tbl_rest_stops` (71 bản ghi): Trạm dừng nghỉ đường dài và cao tốc.
11. `weight_station` (29 bản ghi): Trạm kiểm tra tải trọng xe lưu động / cố định.
12. `tbl_disaster_res_facility` (20 bản ghi): Kho bãi chứa dầm thép, rọ đá, vật tư dự phòng bão lũ.
13. `tbl_ferry` (15 bản ghi): Tàu phà phục vụ vượt sông.
14. `tbl_ferry_terminal` (15 bản ghi): Bến bãi cập phà.
15. `tbl_rescue_vehicle` (8 bản ghi): Phương tiện cứu hộ, xe cứu hộ chuyên dụng.

### 2.6 Hầm Đường bộ, Hành lang và Quản lý Đất (7 datasets - 138 bản ghi)
1. `tbl_tunnel_main` (34 bản ghi): Công trình hầm giao thông đường bộ xuyên núi/đô thị.
2. `tunnel_emergency` (4 bản ghi): Thiết bị thoát hiểm trong hầm.
3. `tunnel_other` (4 bản ghi): Hệ thống kỹ thuật hầm (thông gió, chiếu sáng hầm).
4. `tunnel_work_outside` (4 bản ghi): Công trình phụ trợ bên ngoài cửa hầm.
5. `tbl_infrastructure_row` (14,656 bản ghi): Công trình hạ tầng kỹ thuật nằm trong hành lang an toàn đường bộ.
6. `tbl_land_btra` (12 bản ghi): Quỹ đất thuộc tài sản kết cấu hạ tầng đường bộ.
7. `tbl_its` (10 bản ghi): Trung tâm điều hành giao thông thông minh ITS.
8. `thongtinlandungkhancap` (13 bản ghi): Vị trí dải dừng xe khẩn cấp.
9. `thongtinduanbot` (6 bản ghi): Dự án đầu tư theo phương thức đối tác công tư BOT.
10. `gp_ctdkt` (1 bản ghi): Giấy phép thi công công trình trên đường đang khai thác.

---

## 3. Chi tiết Tier 2: 152 Tập Danh mục Tham chiếu (Reference Catalogs)

- **Đặc trưng:** Tiền tố tệp là `reference_moc_*` (152 files) và tệp `road_sign_catalog.json`.
- **Tổng số bản ghi:** **6,373 bản ghi**.
- **Cấu trúc trường đồng nhất:** `oid`, `vidagis_tableid`, `vidagis_fieldvalue`, `vidagis_fielddisplay`, `vidagis_weight`.
- **Các nhóm danh mục cốt lõi:**
  1. *Danh mục Phân cấp Hạ tầng:* `reference_moc_dbvn_c_c_capduong` (Cấp đường), `reference_moc_dbvn_c_c_loaithanhphan` (Loại thành phần).
  2. *Danh mục Biển báo QCVN 41:* `road_sign_catalog` (381 loại biển báo chuẩn quốc gia kèm hình dạng tam giác, tròn, chữ nhật).
  3. *Danh mục Vật liệu & Kết cấu:* Loại cầu, loại móng mố, loại hộ lan tôn sóng, vật liệu áo đường (bê tông nhựa, bê tông xi măng, đá dăm).
  4. *Danh mục Quản lý:* Danh mục Khu QLĐB, đơn vị bảo dưỡng, loại hình sở hữu.
- **Xử lý Schema:** Nạp toàn bộ vào một bảng duy nhất `reference_catalog` với `catalog_code = substring(filename, 'reference_moc_dbvn_c_c_(.*)')`.

---

## 4. Chi tiết Tier 3: 123 Tập Quản lý Hồ sơ Tài liệu (Documents)

- **Bao gồm:**
  - `document_folders.json`: 121 thư mục gốc quản lý hồ sơ theo đơn vị hành chính và phòng ban.
  - `documents.json`: Danh mục 309 tài liệu kỹ thuật, hồ sơ hoàn công, biểu mẫu giao thông.
  - `document_files_index.json`: Chỉ mục chi tiết 309 file nhị phân (Excel, PDF, CAD) kèm băm SHA-256 và kích thước tệp.
  - 121 tệp `documents_group_*.json`: Liên kết tài liệu theo từng nhóm vị trí địa lý.
- **Xử lý Schema:** 
  - Nạp danh mục cây thư mục vào `document_folder`.
  - Nạp chỉ mục tệp nhị phân và quan hệ liên kết tài sản vào `document_metadata`.

---

## 5. Chi tiết Tier 4: 314 Tập Báo cáo & Thống kê Định kỳ (Reports & Statistics)

- **Bao gồm:**
  1. *Báo cáo chi tiết bảo trì tài sản:* 74 tệp `maintenance_detail_*_chitiet.json` (262,322 bản ghi).
  2. *Báo cáo tổng hợp bảo trì tài sản:* 74 tệp `maintenance_detail_*_tonghop.json` và 148 tệp `maintenance_*` (148 bản ghi).
  3. *Báo cáo thống kê chiều dài mạng lưới đường bộ:* 24 tệp `road_length_2016.json` đến `road_length_2027.json` và `road_length_province_*.json` (1,656 bản ghi).
  4. *Thống kê điểm đen TNGT & Biển báo:* `statistic_road-sign.json` (7 bản ghi), `statistic_remediation-black-spot.json` (0 bản ghi).
- **Phân tích bản chất:**
  - Thực nghiệm kiểm tra dữ liệu cho thấy `maintenance_detail_*_chitiet` chỉ là phép chiếu (SELECT subset of columns) từ bảng tài sản gốc tương ứng kèm theo điều kiện lọc.
  - Ví dụ: `maintenance_detail_tbl_guardrail_chitiet` chỉ chứa `{vidagis_id, km_from, km_to, province_from_id, province_to_id, type_guardrail}`. Toàn bộ các trường này đã có đầy đủ trong `assets/tbl_guardrail.json`.
- **Quyết định Kiến trúc:** **KHÔNG tạo 296 bảng cứng** cho nhóm báo cáo này trong cơ sở dữ liệu vận hành. Lưu nguyên bản trong `raw_dataset_record`, và xây dựng các SQL Views/API động trên `asset_record` để phục vụ màn hình báo cáo.

---

## 6. Chi tiết Tier 5: Dữ liệu Không Nên Import Vào Vận hành

Các tập dữ liệu sau được **loại trừ khỏi tầng dữ liệu vận hành (Curated Layer)**:

1. **`user_guide_tables.json` (1,620 bản ghi):**
   - *Bản chất:* Nội dung bảng cẩm nang, thuật ngữ viết tắt và tài liệu hướng dẫn sử dụng phần mềm được cào từ các trang trợ giúp HTML.
   - *Lý do loại trừ:* Đây là nội dung tài liệu CMS/Helpdesk tĩnh, không liên quan đến thực thể tài sản kỹ thuật đường bộ. Không đưa vào mô hình dữ liệu quan hệ nghiệp vụ.
2. **195 Tệp Module Rỗng (0 bản ghi):**
   - *Bao gồm:* 81 file `documents_group_g_*`, 93 file `maintenance_*` của các hạng mục không có phát sinh dữ liệu, `annual_plans.json` (kế hoạch năm), `statistic_remediation-black-spot.json`.
   - *Lý do loại trừ:* Tránh tạo rác dữ liệu trong tầng curated. Chỉ ghi nhận tên tệp trong `dataset_registry` để kiểm soát đầy đủ 658 tệp nguồn.
3. **Các tệp trùng lặp cấu trúc báo cáo:**
   - 148 file `maintenance_detail_*_tonghop` với tổng số chỉ 148 dòng (mỗi file 1 dòng tổng hợp duy nhất) được thay thế hoàn toàn bằng hàm `COUNT()` và `SUM()` trong PostgreSQL.
