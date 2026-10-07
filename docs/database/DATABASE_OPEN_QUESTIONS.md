# DANH MỤC GIẢ ĐỊNH, RỦI RO VÀ CÂU HỎI MỞ (DATABASE OPEN QUESTIONS)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Danh mục Giả định Thiết kế (Architectural Assumptions)

Toàn bộ các giả định dưới đây được xây dựng dựa trên bằng chứng dữ liệu thực nghiệm đã kiểm chứng trực tiếp từ bộ tệp `C:\Data\kcht_json_2026-10-05`:

### Giả định 1: Hệ quy chiếu và Tọa độ Không gian là WGS 84 (EPSG:4326)
- **Bằng chứng dữ liệu:** 
  - Trong tệp `field_dictionary.json`, các trường tọa độ có diễn giải rõ ràng: `field.from_coordinatex1` mang alias là `"Tọa độ X (WGS84)"`, `field.to_coordinatex` mang alias là `"Tọa độ X (WGS84)"`.
  - Giá trị tọa độ thực tế kiểm tra ngẫu nhiên trên các tệp `road_sphere_mirror.json`, `tbl_road_sign.json`, `tbl_longitudinal.json` đều nằm trong dải: $X \approx 106.7^\circ$ (Kinh độ Đông) và $Y \approx 21.9^\circ$ (Vĩ độ Bắc), tương ứng địa bàn các tỉnh phía Bắc (Lạng Sơn, Tuyên Quang).
- **Hệ quả thiết kế:** Toàn bộ cột hình học trong PostGIS được định nghĩa với SRID chuẩn `4326` (`GEOMETRY(Geometry, 4326)`), sử dụng trực tiếp trên nền bản đồ WebGIS vệ tinh hoặc OpenStreetMap mà không cần chuyển đổi hệ quy chiếu phức tạp.

### Giả định 2: Tính Duy nhất của Khóa Định danh Tài sản (`record_id` & `gid`)
- **Bằng chứng dữ liệu:**
  - Kiểm tra tính duy nhất nội bộ trên 10 tập dữ liệu mẫu: Số lượng bản ghi duy nhất theo trường `id` và trường `gid` bằng 100% tổng số bản ghi (`unique_id == total_records`).
  - Kiểm tra va chạm khóa chéo (Cross-dataset Collisions) trên 194,549 bản ghi thuộc các tập tài sản khác nhau: Kết quả ghi nhận **0 vụ va chạm khóa ID** và **0 vụ va chạm khóa GID**.
- **Hệ quả thiết kế:** Trường `id` gốc (dạng chuỗi, ví dụ `bridge_45228`, `guardrail_521041`) được chọn làm khóa nghiệp vụ tự nhiên (`record_id UNIQUE`) cho bảng `asset_record`, đảm bảo tương thích tuyệt đối với liên kết khóa ngoại từ tài liệu (`vidagis_classpk`) và báo cáo bảo trì (`vidagis_id`).

### Giả định 3: Quy tắc Bóc tách Khóa Quan hệ Phân cấp Cha - Con (`parent_id`)
- **Bằng chứng dữ liệu:**
  - Trong `duonggom.json`: Bản ghi có `parent_id = "17791990530353074_duonggom"`. Truy vết trong `tbl_segment.json` tìm thấy bản ghi có ID `17791990530353074` chính là đoạn tuyến `"QL.2 - Đường tránh TP. Tuyên Quang"`.
  - Trong `tbl_pierstr.json`: Bản ghi có `parent_id = "bridge_22115_tbl_pierstr"`. Truy vết trong `tbl_bridge.json` tìm thấy cầu `"bridge_22115"`.
- **Hệ quả thiết kế:** Hệ thống tự động cắt bỏ phần hậu tố `_<tên_bảng_con>` trong chuỗi `parent_id` để khôi phục khóa tham chiếu chính xác đến thực thể cha trong cây hạ tầng.

### Giả định 4: Bản chất của 296 Tập Dữ liệu `maintenance_*`
- **Bằng chứng dữ liệu:**
  - Kiểm tra cấu trúc các tệp `maintenance_detail_*_chitiet.json` (ví dụ `maintenance_detail_tbl_guardrail_chitiet`): Bản ghi chỉ chứa 6 trường `{km_to, km_from, vidagis_id, province_to_id, type_guardrail, province_from_id}`.
  - Các trường này hoàn toàn trùng khớp với thuộc tính đã có trong bản ghi tài sản gốc `assets/tbl_guardrail.json`.
  - Endpoint sinh ra các tệp này là `/report/get-data-asset-detail` và `/report/get-data-asset-maintenance-report`.
- **Hệ quả thiết kế:** Đây không phải là dữ liệu giao dịch bảo trì độc lập (không có nhật ký sửa chữa, hóa đơn, biên bản nghiệm thu), mà chỉ là các tệp trích xuất báo cáo từ bảng tài sản. Do đó, việc tạo 296 bảng cứng là sai lầm kiến trúc; hệ thống chỉ lưu tại `raw_dataset_record` và cung cấp Dynamic View/API tương ứng trên `asset_record`.

### Giả định 5: Hệ thống Tài khoản và Bảo mật
- **Bằng chứng dữ liệu:**
  - `manifest.json` ghi rõ: *"Không có mật khẩu, cookie hoặc token đăng nhập nào được thêm vào thư mục này"*.
  - Trong tệp `documents.json`, chỉ có thông tin meta về người tải lên: `vidagis_userid: "2"`, `vidagis_username: "admin"`.
- **Hệ quả thiết kế:** Toàn bộ hệ thống quản trị người dùng, vai trò (`ROLE_ADMIN`, `ROLE_OPERATOR`) và phân quyền phải được khởi tạo mới hoàn toàn trong schema `app_user`, `app_role`, `app_permission`.

---

## 2. Danh mục Rủi ro Kỹ thuật (Technical & Business Risks)

| STT | Rủi ro nhận diện | Tác động | Giải pháp kiến trúc phòng ngừa |
| :--- | :--- | :--- | :--- |
| **R1** | **Tài sản dạng đường (Line assets) chỉ có 2 điểm tọa độ đầu/cuối** | Nhiều công trình dạng tuyến (hộ lan, rãnh dọc, đường gom) trong dữ liệu nguồn chỉ có tọa độ điểm đầu (`from_coordinatex/y`) và điểm cuối (`to_coordinatex/y`), không có chuỗi đỉnh (polyline) uốn lượn theo địa hình. Trên bản đồ GIS, đối tượng sẽ hiển thị dưới dạng một đoạn thẳng nối 2 điểm. | Hệ thống lưu tạm đoạn thẳng `ST_MakeLine` trong giai đoạn 1. Giai đoạn sau có thể bổ sung tiến trình khớp hình học (Map Snapping) theo tim tuyến đường chuẩn của Cục Đường bộ. |
| **R2** | **Dữ liệu tọa độ ngoại vi hoặc bằng 0** | Một số bản ghi có tọa độ bằng 0 hoặc nằm ngoài lãnh thổ Việt Nam do người dùng nhập liệu sai sót trong quá khứ. | Triển khai bộ lọc kiểm tra biên độ địa lý ($102 \le X \le 112, 8 \le Y \le 24$). Các bản ghi không hợp lệ được đánh dấu `is_valid = FALSE` và không nạp vào lớp hiển thị bản đồ để tránh làm lệch khung nhìn (zoom extent) của WebGIS. |
| **R3** | **Kích thước nạp dữ liệu thô lớn (~3.86 GB JSON)** | Nạp đồng thời 658 tệp JSON (trong đó có các tệp biển báo ~790MB) có thể gây tràn bộ nhớ JVM (OutOfMemoryError) hoặc làm khóa bảng lâu. | Sử dụng cơ chế nạp luồng dạng Streaming JSON (Jackson Streaming API trong Java hoặc pg_read_file qua Python script chuyên dụng), chia batch 2,000 bản ghi/lần nạp kèm `COMMIT` định kỳ. |
| **R4** | **Hiệu năng truy vấn thuộc tính động trong JSONB** | Nếu người dùng trên website thường xuyên lọc theo các trường nằm sâu trong `attributes` JSONB mà không có chỉ mục, PostgreSQL sẽ phải quét tuần tự (Seq Scan) trên 833,000 dòng. | Tạo chỉ mục `GIN (attributes jsonb_path_ops)`. Nếu có những trường động được lọc với tần suất cao (ví dụ: `duanbot`, `capduong`), sẽ tiến hành migrate bổ sung cột Typed riêng bằng Flyway. |
| **R5** | **Biến động số lượng bản ghi nguồn (`tbl_bridge`)** | Trong `source_manifest.json` có ghi nhận cảnh báo: `{'table': 'tbl_bridge', 'error': 'source_total_changed', 'resolved': True}`. Điều này cho thấy hệ thống nguồn vẫn đang có biến động dữ liệu trong quá trình crawl. | Khóa chính `record_id` kết hợp băm `payload_hash` bảo đảm cơ chế Upsert (nếu ID đã tồn tại thì cập nhật nội dung mới, nếu chưa có thì thêm mới), đảm bảo tính lũy tiến và an toàn dữ liệu. |

---

## 3. Các Câu hỏi Cần Người Dùng / Chủ Đầu tư Quyết định (Open Questions)

Trước khi chuyển sang **Prompt 2 (Viết migration DDL Flyway và Code nạp dữ liệu)**, các quyết định sau cần được người dùng phê duyệt:

### Câu hỏi 1: Lựa chọn Mô hình Bảng Curated cho Tài sản
- **Phương án A (Khuyến nghị):** Hợp nhất toàn bộ 57 tập tài sản vào một bảng duy nhất `asset_record` (kèm bảng không gian `asset_geometry`). Các thuộc tính chung được typed, thuộc tính riêng lưu trong `attributes` JSONB.
  - *Ưu điểm:* Dễ dàng viết API tìm kiếm toàn cục, bản đồ GIS chỉ cần đọc 1 layer duy nhất, quản trị phân quyền tập trung, mã nguồn Spring Boot cực kỳ tinh gọn.
- **Phương án B:** Hợp nhất 52 loại tài sản nhỏ vào `asset_record`, nhưng tách riêng 5 loại tài sản có khối lượng siêu lớn hoặc nghiệp vụ phức tạp thành bảng Curated riêng:
  1. `curated_road_sign` (Biển báo - 222,112 dòng)
  2. `curated_sphere_mirror` (Gương cầu/Cột cần vươn - 191,928 dòng)
  3. `curated_drainage` (Cống rãnh dọc & ngang - 121,877 dòng)
  4. `curated_guardrail` (Hộ lan tôn sóng - 51,697 dòng)
  5. `curated_bridge` (Cầu đường bộ - 11,631 dòng kèm liên kết mố trụ)
  - *Câu hỏi:* Người dùng muốn triển khai theo **Phương án A (Hợp nhất hoàn toàn)** hay **Phương án B (Tách bảng cho top 5 tài sản lớn)**?

---

### Câu hỏi 2: Phạm vi Nạp Dữ liệu của 296 Tập Báo cáo Bảo trì
- **Phương án A (Khuyến nghị):** Nạp toàn bộ 296 tệp `maintenance_*` vào tầng `raw_dataset_record` để bảo lưu dữ liệu gốc, nhưng **không nạp vào tầng Curated**. Phục vụ báo cáo trên website bằng Dynamic SQL View trên `asset_record`.
- **Phương án B:** Nạp thêm 296 tệp này vào một bảng trung gian `maintenance_report_snapshot` để lưu lại đúng nguyên văn kết quả báo cáo tại thời điểm crawl (05/10/2026).
- *Câu hỏi:* Người dùng có đồng ý với Phương án A không, hay cần lưu trữ cả snapshot báo cáo lịch sử?

---

### Câu hỏi 3: Độ mịn Hình học Tuyến (Line Geometry Fidelity)
- **Hiện trạng:** Đối với các tài sản dạng tuyến (`tbl_guardrail`, `duonggom`, `tbl_longitudinal`), dữ liệu nguồn chỉ cung cấp điểm đầu và điểm cuối.
- *Câu hỏi:* Người dùng muốn:
  - Tạm thời lưu đoạn thẳng 2 điểm (`LINESTRING(from_pt, to_pt)`) trong PostGIS?
  - Hay chỉ lưu điểm đầu (`POINT(from_pt)`) đại diện cho vị trí bắt đầu của tài sản?

---

### Câu hỏi 4: Cơ chế Xác thực và Phân quyền Người dùng (Authentication & Authorization)
- **Phương án A:** Sử dụng xác thực cục bộ (Local Database Authentication) qua bảng `app_user` (BCrypt password hash) kết hợp JWT trong Spring Boot 3.
- **Phương án B:** Tích hợp với hệ thống Quản lý Danh tính tập trung (Keycloak / OpenID Connect / SSO Cục Đường bộ).
- *Câu hỏi:* Yêu cầu của dự án hiện tại là triển khai xác thực nội bộ (Phương án A) hay kết nối SSO ngoài (Phương án B)?
