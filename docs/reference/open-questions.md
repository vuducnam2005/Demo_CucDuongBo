# DANH MỤC CÂU HỎI MỞ VÀ VẤN ĐỀ CẦN LÀM RÕ (OPEN_QUESTIONS)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0 (Giai đoạn 0 - Khảo sát và Chốt phạm vi)

---

## 1. NGUYÊN TẮC GHI NHẬN

Tài liệu này ghi lại toàn bộ các câu hỏi mở, ranh giới phạm vi chưa chắc chắn và các điểm cần xin ý kiến quyết định của Chủ đầu tư / Ban Quản lý Dự án trước khi tiến hành code các phân hệ trong các giai đoạn tiếp theo.

---

## 2. DANH MỤC CÁC CÂU HỎI MỞ CẦN CHỐT

### Vấn đề 1: Phê duyệt Ranh giới Phạm vi MVP (P0)
- **Hiện trạng:** Hệ thống có 9 phân hệ chức năng và 658 tập dữ liệu. Để bảo đảm bàn giao sản phẩm chạy được sớm nhất, kiến trúc sư đã đề xuất phân kỳ:
  - **MVP (P0):** Xác thực JWT, Dashboard tổng quan, Cây tài sản, Bảng dữ liệu phân trang trên 3 dataset đại diện (`mst_national_road`, `tbl_bridge`, `tbl_road_sign`), Xem chi tiết công trình, Bản đồ WebGIS cơ bản (BBOX filter) và Tra cứu danh mục tham chiếu.
  - **Giai đoạn tiếp theo (P1 & P2):** Báo cáo bảo trì động, Quản lý tài liệu MinIO, CRUD đầy đủ và Quản trị người dùng nâng cao.
- **Câu hỏi cho Chủ đầu tư:** Chủ đầu tư có đồng ý với ranh giới phân kỳ MVP này không, hay cần đưa thêm phân hệ nào khác vào MVP ngay từ đầu?

---

### Vấn đề 2: Chốt Thư viện Giao diện Duy nhất cho Frontend (Single UI Library)
- **Hiện trạng:** Kế hoạch yêu cầu chọn **duy nhất một UI Library** giữa Ant Design và MUI/Shadcn.
- **Phân tích:**
  - **Ant Design 5 (vi-VN locale) - Khuyến nghị:** Đã có sẵn hệ thống Bảng dữ liệu phức tạp (phân trang, lọc, sắp xếp nhiều cột), Tree view lazy-load, Form sinh theo schema, DatePicker và hỗ trợ hoàn hảo tiếng Việt. Rất phù hợp với phong cách trang điều hành hành chính công tương tự cổng mẫu `kcht.drvn.gov.vn`.
  - **Shadcn UI + Tailwind CSS:** Hiện đại, linh hoạt cao, bundle nhẹ nhưng tốn công tự code các bảng dữ liệu lớn.
- **Câu hỏi cho Chủ đầu tư:** Ban Quản lý Dự án có đồng ý lựa chọn **Ant Design 5 (vi-VN)** làm thư viện UI chuẩn duy nhất cho toàn bộ hệ thống không?

---

### Vấn đề 3: Lựa chọn Nguồn Bản đồ Nền (GIS Basemap Provider) cho WebGIS
- **Hiện trạng:** Bản đồ số WebGIS cần một lớp bản đồ nền (Basemap) để hiển thị các công trình giao thông (cầu, đường, biển báo).
- **Các phương án:**
  - **Phương án A (Khuyến nghị cho Local / Dev):** OpenStreetMap (OSM) công khai (miễn phí, không cần API Key).
  - **Phương án B:** Bản đồ Vệ tinh / Giao thông Mapbox GL / Google Maps (yêu cầu API Key có trả phí).
  - **Phương án C:** Bản đồ nền quốc gia của Cục Đo đạc, Bản đồ và Thông tin địa lý Việt Nam (VN2000 / WGS84, nếu được cấp quyền kết nối).
- **Câu hỏi cho Chủ đầu tư:** Trong môi trường thử nghiệm và bàn giao, hệ thống sẽ sử dụng bản đồ nền nào?

---

### Vấn đề 4: Quy tắc Phân quyền Phạm vi Dữ liệu theo Cấp Đơn vị (Data Scope)
- **Hiện trạng:** Trong dữ liệu nguồn, các công trình được gắn với trường `branch_id` (ví dụ `1`, `2`, `3`, `4` tương ứng 4 Khu Quản lý Đường bộ) và `organization_id` (Sở GTVT / Chi cục QLĐB).
- **Câu hỏi cho Chủ đầu tư:** 
  1. Cán bộ thuộc Khu QLĐB I có được phép xem số liệu của Khu QLĐB II, III, IV hay bị chặn hoàn toàn?
  2. Quyền chỉnh sửa công trình có bị giới hạn tuyệt đối theo `branch_id` của tài khoản đăng nhập hay không?

---

### Vấn đề 5: Phương thức Đồng bộ Tệp Nhị phân Hồ sơ Kỹ thuật (309 Files)
- **Hiện trạng:** Tệp `document_files_index.json` lưu chỉ mục 309 tệp nhị phân (PDF, DWG, DOCX) liên kết tới endpoint `/file-transfer/get-file` của máy chủ cũ.
- **Các phương án:**
  - **Phương án A (Khuyến nghị):** Viết script nạp metadata vào PostgreSQL trước; khi có kết nối mạng tới máy chủ cũ, chạy batch worker tải toàn bộ 309 file về lưu trữ an toàn trong bucket MinIO cục bộ.
  - **Phương án B:** Chỉ lưu metadata; khi người dùng bấm nút tải trên website thì hệ thống mới proxy tải trực tiếp từ máy chủ cũ.
- **Câu hỏi cho Chủ đầu tư:** Ban Quản lý lựa chọn Phương án A (lưu trữ độc lập trên MinIO) hay Phương án B?

---

### Vấn đề 6: Độ mịn và Khớp Hình học Tuyến (Line Geometry Fidelity)
- **Hiện trạng:** Các công trình dạng tuyến (`tbl_guardrail`, `duonggom`, `tbl_longitudinal`) chỉ có 2 điểm tọa độ đầu và cuối. Trên bản đồ giai đoạn 1, chúng sẽ hiển thị dưới dạng một đoạn thẳng nối 2 điểm.
- **Câu hỏi cho Chủ đầu tư:** Chủ đầu tư có đồng ý tạm thời hiển thị đoạn thẳng 2 điểm ở giai đoạn 1 và bổ sung thuật toán khớp hình học (Map Snapping) theo tim đường ở giai đoạn nâng cao không?
