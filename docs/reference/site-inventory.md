# BẢNG TỔNG MỤC TÍNH NĂNG VÀ CHỨC NĂNG HỆ THỐNG (SITE_INVENTORY)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Mục tiêu:** Xây dựng website có luồng nghiệp vụ, bố cục và năng lực quản lý tương đương website mẫu `https://kcht.drvn.gov.vn/dashboard`.  
**Phiên bản:** 1.0.0 (Giai đoạn 0 - Khảo sát và Chốt phạm vi)

---

## 1. TỔNG QUAN DANH MỤC CÁC PHÂN HỆ NGHIỆP VỤ

Dựa trên kết quả khảo sát bộ dữ liệu nguồn tại `C:\Data\kcht_json_2026-10-05` (658 tệp JSON, 1.1 triệu bản ghi) và các endpoint hệ thống hiện hữu, website được cấu thành từ 9 phân hệ chức năng chính:

1. **Phân hệ 1: Xác thực & Phân quyền Người dùng (Authentication & RBAC)**
2. **Phân hệ 2: Bảng Điều hành & Thống kê Tổng quan (Executive Dashboard)**
3. **Phân hệ 3: Cây Tài sản & Quản lý Danh sách Công trình (Asset Tree & Data Grid)**
4. **Phân hệ 4: Bản đồ Số WebGIS Điều hành (Interactive WebGIS Map)**
5. **Phân hệ 5: Báo cáo Thống kê & Kế hoạch Bảo trì (Maintenance & Reports)**
6. **Phân hệ 6: Quản lý Hồ sơ & Tài liệu Kỹ thuật (Technical Documents & Files)**
7. **Phân hệ 7: Quản trị Danh mục Tham chiếu (Reference Catalogs & Metadata)**
8. **Phân hệ 8: Điều phối Nạp & Đối soát Dữ liệu (Ingestion Jobs & Reconciliation)**
9. **Phân hệ 9: Quản trị Người dùng & Kiểm toán Hệ thống (User Administration & Audit)**

---

## 2. ĐẶC TẢ CHI TIẾT TỪNG CHỨC NĂNG (FUNCTIONAL INVENTORY)

---

### PHÂN HỆ 1: XÁC THỰC & PHÂN QUYỀN (AUTHENTICATION & RBAC)

#### Chức năng 1.1: Đăng nhập Hệ thống (User Login)
- **Mục đích:** Xác thực danh tính cán bộ, cấp phát JWT Token (Access Token & Refresh Token) để truy cập hệ thống theo đúng vai trò và phạm vi quản lý.
- **Role được phép:** Mọi người dùng chưa xác thực (Anonymous).
- **Input:**
  - `username` (string, required): Tên tài khoản.
  - `password` (string, required): Mật khẩu.
  - `rememberMe` (boolean, optional): Ghi nhớ phiên đăng nhập.
- **Output:**
  - `accessToken` (string, JWT): Thời hạn 15 - 30 phút.
  - `refreshToken` (string): Thời hạn 7 ngày (hoặc HttpOnly Cookie).
  - `user`: Thông tin `{id, username, fullName, roles, organizationId, branchId}`.
- **Dataset / API liên quan:** `POST /api/auth/login`, bảng `app_user`, `app_role`.
- **Bộ lọc / Sắp xếp / Phân trang:** Không áp dụng.
- **Trạng thái rỗng (Empty State):** Không áp dụng.
- **Trạng thái lỗi (Error State):**
  - Sai tên đăng nhập/mật khẩu: Thông báo *"Tên đăng nhập hoặc mật khẩu không chính xác"*.
  - Khóa tài khoản: *"Tài khoản tạm thời bị khóa do nhập sai quá 5 lần"*.
- **Acceptance Criteria:**
  - Mật khẩu được băm kiểm tra bằng BCrypt.
  - Không lộ thông tin nhạy cảm trong response.
  - Sau khi đăng nhập thành công, tự động chuyển hướng về `/dashboard`.

#### Chức năng 1.2: Đăng xuất & Thu hồi Phiên (User Logout)
- **Mục đích:** Hủy phiên làm việc hiện tại, đưa token vào blacklist để bảo đảm an toàn.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** Refresh Token hoặc Header `Authorization: Bearer <token>`.
- **Output:** HTTP `204 No Content`.
- **Dataset / API liên quan:** `POST /api/auth/logout`.
- **Acceptance Criteria:** Client xóa sạch token trong local storage/cookie; chuyển hướng về trang `/login`.

---

### PHÂN HỆ 2: BẢNG ĐIỀU HÀNH & TỔNG QUAN (EXECUTIVE DASHBOARD)

#### Chức năng 2.1: Thẻ Chỉ số Tổng quan (KPI Metric Cards)
- **Mục đích:** Cung cấp cho lãnh đạo Cục Đường bộ cái nhìn tổng quan về quy mô mạng lưới tài sản: Tổng số km đường quốc lộ, tổng số cầu, tổng số biển báo, tổng số điểm đen cần xử lý.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:**
  - `branchId` (tùy chọn): Lọc theo Khu Quản lý Đường bộ (Khu I, II, III, IV).
  - `provinceId` (tùy chọn): Lọc theo tỉnh/thành phố.
- **Output:** Dữ liệu JSON chứa các chỉ số:
  - `totalNationalRoadLength`: Tổng km quốc lộ đang quản lý.
  - `totalBridges`: Số lượng cầu đường bộ (11,631 cầu).
  - `totalRoadSigns`: Số lượng biển báo (222,112 biển).
  - `totalBlackSpots`: Số lượng điểm đen tai nạn.
  - `totalDocuments`: Số lượng hồ sơ tài liệu lưu trữ (309 tệp).
- **Dataset / API liên quan:** `GET /api/dashboard/summary` và các endpoint `GET /api/dashboard/stats/*`, bảng `asset_record`, `document_metadata`.
- **Bộ lọc:** `branchId`, `provinceId`.
- **Trạng thái rỗng:** Hiển thị số `0` và biểu tượng thông báo không có số liệu cho phạm vi chọn.
- **Trạng thái lỗi:** Hiển thị thẻ skeleton màu xám kèm nút *"Tải lại dữ liệu"*.
- **Acceptance Criteria:** Dữ liệu được tính toán tổng hợp từ phía server (Server-side aggregation), không tính toán trên hàng trăm nghìn dòng trong browser. Thời gian phản hồi $< 300\text{ms}$.

#### Chức năng 2.2: Biểu đồ Cơ cấu Tài sản theo Địa bàn & Phân loại
- **Mục đích:** Trực quan hóa phân bố tài sản theo địa bàn 63 tỉnh thành và theo trạng thái kỹ thuật (Tốt, Trung bình, Kém, Hư hỏng).
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `metricType` (asset_type / state / province).
- **Output:** Dữ liệu biểu đồ cột / tròn `{label, value, percentage}`.
- **Dataset / API liên quan:** `GET /api/dashboard/stats/branches`, `/stats/datasets`, `/stats/road-signs`, `/stats/road-lengths`.
- **Acceptance Criteria:** Hỗ trợ tooltip chi tiết khi rê chuột; click vào cột biểu đồ tự động chuyển hướng sang Danh sách Tài sản với bộ lọc tương ứng.

---

### PHÂN HỆ 3: CÂY TÀI SẢN & QUẢN LÝ DANH SÁCH (ASSET TREE & DATA GRID)

#### Chức năng 3.1: Điều hướng Cây Danh mục Tài sản (Hierarchical Asset Tree)
- **Mục đích:** Hiển thị cây cấu trúc danh mục 57 loại tài sản kết cấu hạ tầng đường bộ phân nhóm theo chuyên môn (Tuyến đường, Cầu, Thoát nước, Báo hiệu, Cơ sở phụ trợ, Hầm).
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** Từ khóa tìm kiếm loại tài sản (`searchKey`).
- **Output:** Cấu trúc cây đa cấp `{id, label, datasetKey, recordCount, icon, children}`.
- **Dataset / API liên quan:** `GET /api/v1/datasets`, bảng `dataset_registry`.
- **Acceptance Criteria:**
  - Tải nhanh, hiển thị số lượng bản ghi tương ứng bên cạnh tên từng loại tài sản.
  - Nhấp chuột vào một nút lá sẽ kích hoạt tải danh sách bản ghi của dataset đó ở khung bên phải.

#### Chức năng 3.2: Bảng Dữ liệu Động theo Siêu dữ liệu (Dynamic Metadata Data Grid)
- **Mục đích:** Hiển thị danh sách các công trình thuộc dataset đã chọn, với các cột tiêu đề tiếng Việt sinh động từ `field_dictionary.json` / `dataset_field`.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:**
  - `datasetKey` (bắt buộc, path parameter): ví dụ `tbl_bridge`, `tbl_road_sign`, `mst_national_road`.
  - `page` (int, default 0): Trang hiện tại.
  - `size` (int, default 20, max 100): Kích thước trang.
  - `sort` (string, optional): Cột sắp xếp, ví dụ `kmFrom,asc`.
  - `q` (string, optional): Từ khóa tìm kiếm toàn cục.
  - `routeCode`, `provinceId`, `state`: Bộ lọc thuộc tính.
- **Output:** Cấu trúc phân trang chuẩn:
  - `content`: Danh sách bản ghi `{recordKey, name, routeCode, kmFrom, kmTo, provinceName, state, attributes, hasGeometry}`.
  - `page`, `size`, `totalElements`, `totalPages`.
- **Dataset / API liên quan:**
  - `GET /api/v1/datasets/{datasetKey}/metadata`: Lấy danh sách cột và cấu hình hiển thị.
  - `GET /api/v1/datasets/{datasetKey}/records`: Lấy dữ liệu bản ghi.
- **Bộ lọc:** Lọc theo tuyến quốc lộ (`routeCode`), Tỉnh/thành (`provinceId`), Tình trạng kỹ thuật (`state`).
- **Sắp xếp:** Sắp xếp an toàn theo Allowlist được định nghĩa trong siêu dữ liệu.
- **Phân trang:** Server-side pagination, cố định `size <= 100`.
- **Trạng thái rỗng:** Hiển thị hình minh họa "Không tìm thấy công trình nào phù hợp với bộ lọc", kèm nút "Xóa bộ lọc".
- **Trạng thái lỗi:** Thông báo lỗi kèm mã `errorCode`, giữ nguyên bảng rỗng không làm vỡ giao diện.
- **Acceptance Criteria:**
  - Tải mượt mà trên các dataset lớn (như `tbl_road_sign` 222,112 bản ghi).
  - Có nút chuyển nhanh vị trí sang màn hình Bản đồ WebGIS.

#### Chức năng 3.3: Xem Chi tiết Hồ sơ Công trình (Asset Detail View)
- **Mục đích:** Hiển thị toàn bộ thông tin lý lịch kỹ thuật của một công trình tài sản, bao gồm các thuộc tính mở rộng JSONB, hình học tọa độ, công trình con (nhịp/mố trụ), và tài liệu hoàn công đính kèm.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `datasetKey` và `recordKey` (ví dụ `bridge_45228`).
- **Output:** Dữ liệu chi tiết bản ghi, tọa độ PostGIS, danh sách tài liệu đính kèm, danh sách thực thể con (nếu có `parent_id`).
- **Dataset / API liên quan:** `GET /api/v1/datasets/{datasetKey}/records/{recordKey}`.
- **Acceptance Criteria:** Hiển thị đầy đủ thông tin tab: "Thông tin chung", "Thuộc tính kỹ thuật", "Hồ sơ tài liệu đính kèm", "Bản đồ vị trí".

---

### PHÂN HỆ 4: BẢN ĐỒ SỐ WEBGIS ĐIỀU HÀNH (INTERACTIVE WEBGIS)

#### Chức năng 4.1: Bản đồ Không gian Địa lý Đa lớp (Multi-layer WebGIS Map)
- **Mục đích:** Hiển thị trực quan toàn bộ mạng lưới hạ tầng đường bộ trên nền bản đồ số (bản đồ giao thông, bản đồ vệ tinh), hỗ trợ bật/tắt các lớp chuyên đề (Cầu, Biển báo, Hộ lan, Rãnh dọc, Hầm).
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:**
  - Bounding Box: `bbox=minLon,minLat,maxLon,maxLat`.
  - Danh sách lớp đang bật: `layers=tbl_bridge,tbl_road_sign,...`.
  - Mức thu phóng: `zoom`.
- **Output:** Chuẩn GeoJSON RFC 7946 `FeatureCollection` (Point, LineString).
- **Dataset / API liên quan:** `GET /api/v1/datasets/{datasetKey}/geo?bbox=...`, bảng `asset_geometry`.
- **Bộ lọc:** Lọc theo khung nhìn (BBOX), lọc theo tuyến, tỉnh, trạng thái.
- **Trạng thái rỗng:** Khung nhìn không có đối tượng hiển thị thông báo "Không có công trình trong phạm vi bản đồ này".
- **Trạng thái lỗi:** Nếu tải lớp không gian thất bại, hiển thị toast cảnh báo nhưng không làm sập bản đồ nền.
- **Acceptance Criteria:**
  - Với các tập dữ liệu dày đặc (> 2,000 điểm như biển báo), áp dụng **Clustering** (gom cụm) hoặc Vector Tiles để đảm bảo bản đồ đạt 60fps trên trình duyệt.
  - Nhấp vào đối tượng trên bản đồ hiển thị Popup thông tin tóm tắt: Tên công trình, Tuyến, Lý trình, Tình trạng, và liên kết "Xem chi tiết".

#### Chức năng 4.2: Tìm kiếm và Định vị Tài sản trên Bản đồ (Locate & Zoom to Asset)
- **Mục đích:** Cho phép người dùng nhập mã công trình hoặc tuyến đường và tự động di chuyển khung nhìn bản đồ (Fly-to / Zoom-to) đến vị trí đối tượng.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `recordKey` hoặc tọa độ `(lat, lon)`.
- **Output:** Di chuyển khung nhìn WebGIS và mở Popup thông tin đối tượng.
- **Dataset / API liên quan:** `GET /api/v1/datasets/{datasetKey}/records/{recordKey}`.

---

### PHÂN HỆ 5: BÁO CÁO THỐNG KÊ & KẾ HOẠCH BẢO TRÌ (MAINTENANCE & REPORTS)

#### Chức năng 5.1: Báo cáo Chiều dài Tuyến Đường bộ (Road Length Report)
- **Mục đích:** Thống kê tổng chiều dài mạng lưới quốc lộ, đường tỉnh theo địa bàn từng tỉnh/thành phố hoặc theo Khu QLĐB.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `provinceId`, `routeCode`, `year`.
- **Output:** Bảng dữ liệu thống kê chiều dài (Km), tỷ lệ mặt đường nhựa/bê tông, cấp kỹ thuật đường.
- **Dataset / API liên quan:** `GET /api/v1/reports/road-length`, `GET /api/v1/reports/road-length-province`.
- **Bộ lọc / Sắp xếp:** Theo tỉnh, theo tuyến, sắp xếp theo tổng chiều dài giảm dần.
- **Xuất dữ liệu:** Hỗ trợ xuất file Excel (`.xlsx`) hoặc PDF.
- **Acceptance Criteria:** Số liệu được tổng hợp động từ dữ liệu chuẩn hóa của `asset_record` (`mst_national_road`, `tbl_segment`), đảm bảo tính chính xác 100%.

#### Chức năng 5.2: Báo cáo Bảo trì Tài sản Tổng hợp & Chi tiết
- **Mục đích:** Thay thế 296 tệp `maintenance_*` cũ bằng các báo cáo động, cho phép theo dõi kế hoạch và khối lượng công tác bảo trì định kỳ cho từng loại công trình (hộ lan, rãnh, cầu).
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `datasetKey`, `branchId`, `provinceId`, `fromKm`, `toKm`.
- **Output:** Danh sách các đoạn công trình cần bảo trì kèm khối lượng ước tính và giá trị bảo trì (`maintain_value`).
- **Dataset / API liên quan:** `GET /api/v1/reports/maintenance-summary`, `GET /api/v1/reports/maintenance-detail`.
- **Acceptance Criteria:** Cho phép lọc theo khoảng lý trình và xuất bảng báo cáo phục vụ lập kế hoạch vốn bảo trì đường bộ.

#### Chức năng 5.3: Thống kê & Xử lý Điểm đen Tai nạn Giao thông
- **Mục đích:** Theo dõi các vị trí điểm đen, điểm tiềm ẩn tai nạn giao thông trên toàn quốc và tiến độ xử lý khắc phục.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `routeCode`, `provinceId`, `status`.
- **Output:** Danh sách điểm đen, tọa độ, lý trình, số vụ tai nạn, biện pháp khắc phục.
- **Dataset / API liên quan:** `GET /api/v1/asset/get-list-statistic-remediation-black-spot`.
- **Acceptance Criteria:** Tích hợp cảnh báo trực quan trên bản đồ WebGIS với biểu tượng màu đỏ nhấp nháy.

---

### PHÂN HỆ 6: QUẢN LÝ HỒ SƠ & TÀI LIỆU KỸ THUẬT (DOCUMENTS & FILES)

#### Chức năng 6.1: Duyệt Cây Thư mục Hồ sơ Kỹ thuật (Document Folder Tree)
- **Mục đích:** Quản lý và tra cứu 121 thư mục hồ sơ hoàn công, bản vẽ thiết kế, hồ sơ kiểm định kỹ thuật theo đơn vị quản lý và công trình.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `parentId` (hoặc root).
- **Output:** Danh sách thư mục con `{folderId, folderName, organizationId, fileCount, children}`.
- **Dataset / API liên quan:** `GET /api/v1/documents/folders`, bảng `document_folder`.
- **Acceptance Criteria:** Hiển thị cây thư mục phân cấp mượt mà, bấm vào thư mục hiển thị danh sách tệp đính kèm.

#### Chức năng 6.2: Tra cứu & Tải xuống Tệp Nhị phân An toàn (File Download via MinIO)
- **Mục đích:** Cho phép cán bộ tải về các tệp hồ sơ (PDF, Word, Excel, CAD/DWG, ảnh) với thời hạn truy cập kiểm soát an toàn qua Pre-signed URL.
- **Role được phép:** `viewer`, `editor`, `manager`, `admin` (kiểm tra phân quyền theo đơn vị quản lý).
- **Input:** `fileId`.
- **Output:** URL có chữ ký điện tử: `{downloadUrl, expiresAt}` (thời hạn 15 phút).
- **Dataset / API liên quan:** `GET /api/v1/documents/files/{fileId}/download-url`, MinIO Object Storage.
- **Acceptance Criteria:**
  - Không truyền đường dẫn vật lý máy chủ cho người dùng.
  - Tự động ghi nhận `audit_log` thao tác tải tệp của người dùng.

#### Chức năng 6.3: Tải lên Tệp Hồ sơ Kỹ thuật Mới (File Upload)
- **Mục đích:** Đính kèm tài liệu mới vào công trình tài sản.
- **Role được phép:** `editor`, `manager`, `admin`.
- **Input:** `file` (multipart nhị phân), `assetId`, `folderId`, `description`.
- **Output:** `{fileId, originalName, bytes, sha256, mimeType, createdAt}`.
- **Dataset / API liên quan:** `POST /api/v1/documents/upload`.
- **Acceptance Criteria:**
  - Kiểm tra Magic Bytes bằng Apache Tika (chặn tuyệt đối file thực thi `.exe`, `.bat`, `.sh`, `.php`).
  - Đổi tên tệp thành UUID trên MinIO, bảo đảm an toàn chống tấn công Path Traversal.

---

### PHÂN HỆ 7: QUẢN TRỊ DANH MỤC THAM CHIẾU (REFERENCE CATALOGS)

#### Chức năng 7.1: Tra cứu 152 Danh mục Tham chiếu
- **Mục đích:** Tra cứu các danh mục chuẩn hóa của ngành đường bộ (loại biển báo, loại vật liệu mố trụ, cấp đường, đơn vị quản lý, tình trạng kỹ thuật).
- **Role được phép:** `viewer`, `editor`, `manager`, `admin`.
- **Input:** `catalogCode` (ví dụ `cat_bridge_type`, `cat_road_class`), `q` (tìm kiếm).
- **Output:** Danh sách các mục danh mục `{code, name, description, orderIndex, isActive}`.
- **Dataset / API liên quan:** `GET /api/v1/reference-catalogs`, bảng `reference_catalog`.
- **Acceptance Criteria:** Áp dụng bộ nhớ đệm Caffeine Cache phía backend để tăng tốc độ phản hồi $< 10\text{ms}$.

---

### PHÂN HỆ 8: ĐIỀU PHỐI NẠP & ĐỐI SOÁT DỮ LIỆU (INGESTION & RECONCILIATION)

#### Chức năng 8.1: Giám sát Tiến trình Nạp Dữ liệu (Import Job Monitoring)
- **Mục đích:** Cho phép Quản trị viên theo dõi trạng thái các phiên nạp dữ liệu từ tệp JSON nguồn (Job ID, tệp đang nạp, số dòng thành công, số dòng bỏ qua, số dòng lỗi, thời gian chạy).
- **Role được phép:** `admin`.
- **Input:** `jobId` (optional), `page`, `size`.
- **Output:** Danh sách phiên nạp `{jobId, status, totalFiles, processedFiles, recordsRead, recordsInserted, recordsSkipped, recordsFailed, startTime, endTime}`.
- **Dataset / API liên quan:** `GET /api/v1/import/jobs`, bảng `import_job`, `import_file`.
- **Acceptance Criteria:** Hiển thị thanh tiến độ phần trăm (Progress Bar), tự động làm mới trạng thái mỗi 5 giây.

#### Chức năng 8.2: Tra cứu Nhật ký Lỗi Bản ghi (Import Error Log)
- **Mục đích:** Xem chi tiết các dòng dữ liệu bị lỗi trong quá trình nạp để đối soát và xử lý.
- **Role được phép:** `admin`.
- **Input:** `fileId`, `page`, `size`.
- **Output:** Danh sách lỗi `{lineNo, rawData, errorMessage, errorType, createdAt}`.
- **Dataset / API liên quan:** `GET /api/v1/import/errors`, bảng `import_error`.
- **Acceptance Criteria:** Cho phép lọc theo mã tệp và xuất báo cáo lỗi đối soát.

---

### PHÂN HỆ 9: QUẢN TRỊ NGƯỜI DÙNG & KIỂM TOÁN (IAM & AUDIT)

#### Chức năng 9.1: Quản lý Tài khoản & Phân quyền (User Management)
- **Mục đích:** Thêm mới, cập nhật thông tin, kích hoạt/khóa tài khoản cán bộ và gán vai trò (`viewer`, `editor`, `manager`, `admin`).
- **Role được phép:** `admin`.
- **Dataset / API liên quan:** `/api/auth/users`, bảng `app_user`, `app_role`.

#### Chức năng 9.2: Tra cứu Nhật ký Kiểm toán (Audit Log Viewer)
- **Mục đích:** Ghi nhận và hiển thị toàn bộ thao tác quan trọng trong hệ thống (Đăng nhập, thêm/sửa/xóa tài sản, tải tài liệu, nạp dữ liệu).
- **Role được phép:** `admin`.
- **Dataset / API liên quan:** `GET /api/audit-logs`, bảng `audit_log`.
- **Acceptance Criteria:** Bản ghi kiểm toán bất biến (Immutable), không có quyền xóa.

---

## 3. DANH SÁCH ƯU TIÊN TRIỂN KHAI CHO MVP (MVP PRIORITY MATRIX)

Nhằm tối ưu hóa tiến độ và kiểm soát chất lượng, các tính năng được phân hạng theo 3 mức ưu tiên:

| Mức ưu tiên | Nhóm chức năng | Danh sách chức năng cụ thể | Lý do phân hạng |
| :---: | :--- | :--- | :--- |
| **P0 (Must Have - Bắt buộc cho MVP)** | **Khung xương sống & Dữ liệu Lõi** | - 1.1 Đăng nhập & Xác thực JWT.<br>- 2.1 Thẻ chỉ số Dashboard tổng quan.<br>- 3.1 Cây tài sản theo metadata.<br>- 3.2 Bảng dữ liệu phân trang trên 3 dataset lõi (`tbl_bridge`, `tbl_road_sign`, `mst_national_road`).<br>- 3.3 Xem chi tiết hồ sơ công trình.<br>- 4.1 Bản đồ WebGIS cơ bản hiển thị Point/Line theo BBOX.<br>- 7.1 Tra cứu danh mục tham chiếu. | Tạo nên luồng sử dụng hoàn chỉnh (Vertical Slice) cho phép người dùng đăng nhập, xem dashboard, tìm kiếm công trình trên bảng biểu và định vị trên bản đồ số. |
| **P1 (Should Have - Giai đoạn 2)** | **Báo cáo, Tài liệu & CRUD Nghiệp vụ** | - 4.2 Tìm kiếm & Fly-to trên WebGIS.<br>- 5.1 Báo cáo chiều dài quốc lộ theo tuyến và theo tỉnh.<br>- 5.2 Báo cáo bảo trì tổng hợp & chi tiết.<br>- 6.1 Cây thư mục hồ sơ tài liệu.<br>- 6.2 Tải tệp tài liệu an toàn từ MinIO.<br>- 8.1 Giám sát nạp dữ liệu Import Worker.<br>- Thêm CRUD cơ bản cho các dataset được phép sửa. | Hoàn thiện các phân hệ báo cáo, quản lý tài liệu hồ sơ hoàn công và mở rộng CRUD dữ liệu. |
| **P2 (Nice to Have - Tối ưu Vận hành)** | **Nâng cao & Tối ưu hóa** | - 5.3 Bản đồ cảnh báo điểm đen tai nạn động.<br>- 6.3 Tải lên tài liệu kỹ thuật mới kèm kiểm tra virus.<br>- 9.1 & 9.2 Quản trị người dùng nâng cao & Audit Log.<br>- Vector Tiles (MVT) nâng cao cho WebGIS khi tải cực lớn.<br>- Cơ chế đồng bộ tự động dữ liệu từ hệ thống nguồn bên ngoài. | Tối ưu hóa vận hành quy mô lớn, báo cáo tự động và tính năng chuyên sâu. |
