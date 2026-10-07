# 0008. QUẢN TRỊ DỮ LIỆU CURATED, CRUD LINH HOẠT VÀ IMPORT BATCH AN TOÀN

- **Trạng thái:** ĐÃ PHÊ DUYỆT (ACCEPTED)
- **Ngày quyết định:** 2026-10-06
- **Tác giả:** Kiến trúc sư Phần mềm & Coding Agent KCHT
- **Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB) - Cục Đường bộ Việt Nam

---

## 1. BỐI CẢNH VÀ YÊU CẦU (CONTEXT & REQUIREMENTS)

Trong hệ thống Quản lý KCHT ĐB, dữ liệu kết cấu hạ tầng giao thông (biển báo, cầu, quốc lộ, v.v.) được nạp từ các nguồn dữ liệu ban đầu vào tầng dữ liệu thô (`raw_dataset_record`). Để phục vụ công tác quản lý chuyên ngành, cập nhật hiện trạng khai thác và bảo trì, hệ thống đặt ra các yêu cầu khắt khe về quản trị và CRUD:

1. **Bảo toàn Tính Bất biến của Tầng Raw (Raw Layer Immutability):**
   - Tuyệt đối **không** được cập nhật hoặc xóa bản ghi trong `raw_dataset_record` như một bản ghi nghiệp vụ.
   - Mọi thao tác CRUD chỉ được phép tác động vào tầng dữ liệu tinh chọn/chính thức (`asset_record`) và lưu vết liên kết phả hệ `raw_record_id`.
   - Nếu bản ghi nghiệp vụ cần sửa đổi mà chưa được tinh chọn (chưa có trong `asset_record`), hệ thống phải thực hiện chuyển giao/tinh chọn tự động (promote) sang `asset_record` trước khi áp dụng thay đổi.
2. **Khóa Lạc quan (Optimistic Locking):**
   - Đảm bảo tính toàn vẹn dữ liệu khi nhiều cán bộ quản trị hoặc kỹ sư giao thông cùng truy cập và cập nhật đồng thời trên một tài sản hạ tầng.
   - Sử dụng trường `version` kết hợp `@Version` của JPA; nếu người dùng gửi yêu cầu với số version cũ hơn phiên bản hiện hành trong CSDL, hệ thống lập tức từ chối và trả về HTTP `409 Conflict`.
3. **Xóa Mềm Nghiệp Vụ (Soft Delete):**
   - Khi xóa một tài sản kết cấu hạ tầng, không xóa vật lý (hard delete) khỏi CSDL nhằm tránh làm mất vết lịch sử công trình.
   - Bản ghi được đánh dấu `is_deleted = true`, cập nhật `deleted_at = NOW()` và `deleted_by = currentUser`.
   - Tất cả các truy vấn hiển thị, tổng hợp báo cáo và tìm kiếm thông thường đều tự động loại bỏ các bản ghi đã xóa mềm.
4. **Bảo Vệ Hình Học (Geometry) và Thuộc Tính Hệ Thống:**
   - Các trường thuộc tính hệ thống (`id`, `raw_record_id`, `record_id`, `version`, `created_at`) và tọa độ/hình học không gian (`geom`) được bảo vệ nghiêm ngặt, không cho phép chỉnh sửa tùy tiện qua form CRUD thông thường nếu chưa có quy trình kiểm định trắc địa hoặc số hóa GIS chuyên dụng.
5. **Form Động Sinh từ Schema kết hợp Override Trường Chuyên Ngành:**
   - Giao diện form chỉnh sửa được tự động dựng từ schema định nghĩa trường (`DatasetField`), tự động nhận diện kiểu dữ liệu (chuỗi, số, ngày tháng, JSON, bool).
   - Cho phép ghi đè (override) các trường đặc thù: `branch_id` (chọn đơn vị quản lý từ danh sách Khu QLĐB), `state` (trạng thái duyệt dữ liệu), `maintain_value` (định dạng tiền tệ VNĐ), `km_from` và `km_to` (kiểm tra tính hợp lệ lý trình: `km_from <= km_to`).
6. **Nhập Dữ liệu Batch (CSV/JSON Import) với Dry-run và Rollback An toàn:**
   - Hỗ trợ nhập tệp dữ liệu CSV hoặc JSON hàng loạt.
   - Cung cấp tính năng **Xem trước & Kiểm thử (Preview & Dry-run)**: Phân tích trước dữ liệu, phát hiện lỗi dòng, kiểm tra tính hợp lệ của schema và quy tắc nghiệp vụ trước khi ghi thật vào CSDL.
   - Cơ chế Transactional Rollback: Khi thực thi nhập batch, nếu có lỗi vi phạm ràng buộc dữ liệu nghiêm trọng, toàn bộ batch sẽ bị rollback để tránh tình trạng dữ liệu dở dang (partial insert).
7. **Phân Quyền Chi Tiết theo Dataset & Hành Động (RBAC):**
   - `ROLE_VIEWER`: Chỉ có quyền đọc (Read-only); bị chặn `403 Forbidden` khi gọi bất kỳ API tạo, sửa, xóa hoặc import.
   - `ROLE_EDITOR`: Được phép thêm mới và cập nhật bản ghi; bị chặn `403 Forbidden` khi thực hiện thao tác xóa tài sản.
   - `ROLE_MANAGER` & `ROLE_ADMIN`: Toàn quyền thêm, sửa, xóa mềm và thực hiện import batch dữ liệu.
8. **Kiểm Toán Toàn Diện (Audit Logging):**
   - Mọi thao tác thêm mới (`ASSET_CREATE`), cập nhật (`ASSET_UPDATE`), xóa mềm (`ASSET_DELETE`) và nhập khẩu (`ASSET_IMPORT`) đều được ghi vết vào bảng `audit_log` kèm tên người thực hiện, thời điểm, IP và chi tiết thay đổi.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC VÀ THIẾT KẾ (ARCHITECTURAL DECISIONS)

### 2.1. Sơ đồ Luồng Xử Lý CRUD và Phân Quyền

```
+---------------------------------------------------------------------------------------------------+
|                                          FRONTEND (REACT + VITE)                                   |
|  - AssetListPage: Bổ sung nút Thêm mới, Nhập dữ liệu và cột Thao tác (Sửa, Xóa)                   |
|  - AssetFormModal: Dynamic Form từ Schema, overrides (Khu QLĐB, Trạng thái, Lý trình, Tiền tệ)     |
|  - AssetImportModal: Upload CSV/JSON, Preview thống kê, Dry-run Validation, Tiến trình & Rollback|
|  - RBAC UI Guards: Ẩn/hiện hoặc vô hiệu hóa các nút chức năng theo vai trò người dùng             |
+-------------------------------------------------+-------------------------------------------------+
                                                  | REST API (Bearer Token / X-User-Role)
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                    BACKEND (SPRING BOOT 3.3.4)                                    |
|  - DatasetApiController: Expose POST, PUT, DELETE, POST /preview, POST /import                     |
|  - DatasetPermissionService: Kiểm tra quyền chi tiết (read, create, update, delete, import)       |
|  - AssetCrudService: Xử lý nghiệp vụ lõi:                                                         |
|    ├── Schema & Range Validation (km_from <= km_to, bắt buộc tên)                                 |
|    ├── Raw-to-Curated Promotion (Bảo toàn bất biến raw_dataset_record)                           |
|    ├── Optimistic Locking Check (So khớp version, ném ConflictException khi bất đồng bộ)         |
|    ├── Soft Delete (Đánh dấu is_deleted = true, lưu deleted_at, deleted_by)                       |
|    ├── Batch Import Processor (Dry-run, Parsing, Validation, Transactional Batch Commit)           |
|    └── Audit Logging (Ghi vết vào audit_log qua AuditLogRepository)                               |
+-------------------------------------------------+-------------------------------------------------+
                                                  | JPA / Hibernate / Transaction
                                                  v
+-------------------------------------------------+-------------------------------------------------+
|                                      POSTGRESQL 16 + POSTGIS                                      |
|  - raw_dataset_record: Bất biến (Immutable), giữ nguyên vẹn dữ liệu gốc nạp từ JSON               |
|  - asset_record: Lưu trữ phiên bản tinh chọn, trường version (@Version), is_deleted, audit fields |
|  - audit_log: Lưu vết lịch sử thao tác người dùng (ACTION, USERNAME, ENTITY_ID, PAYLOAD)          |
+---------------------------------------------------------------------------------------------------+
```

### 2.2. Chi Tiết Các Quyết Định Kỹ Thuật

#### A. Chiến Lược Bất Biến Tầng Raw và Thúc Đẩy Dữ Liệu Tinh Chọn (Promotion Strategy)
- Khi người dùng gửi yêu cầu sửa (`PUT`) hoặc xóa (`DELETE`) một bản ghi bằng `recordKey`:
  1. Trước tiên, tìm kiếm trong `asset_record` theo `record_id` (hoặc `id` nếu truyền khóa số).
  2. Nếu tìm thấy, thực hiện kiểm tra `version` và cập nhật trực tiếp trên `asset_record`.
  3. Nếu bản ghi chưa từng được tinh chọn (chỉ tồn tại trong `raw_dataset_record`), hệ thống tự động khởi tạo một bản ghi mới trong `asset_record`, thiết lập `raw_record_id = raw.id`, sao chép dữ liệu từ `raw.data_` rồi áp dụng các thay đổi nghiệp vụ mới.
  4. Bảng `raw_dataset_record` hoàn toàn không bị thay đổi hoặc xóa bỏ bất kỳ dòng nào.

#### B. Cơ Chế Khóa Lạc Quan (Optimistic Locking)
- Entity `AssetRecordEntity` có trường `@Version private Integer version`.
- Khi client gọi `PUT` hoặc `DELETE`:
  - Client gửi lên thuộc tính `version` trong body DTO hoặc query parameter.
  - Service đối chiếu `clientVersion` với `entity.getVersion()`.
  - Nếu không khớp, hệ thống ném `ConflictException("Bản ghi đã bị thay đổi bởi người dùng khác. Vui lòng tải lại trang...")`, trả về HTTP status `409 Conflict`.

#### C. Quy Trình Import Batch An Toàn
- **Giai đoạn 1: Preview & Dry-run (`POST /api/datasets/{dataset}/import/preview`)**
  - Hệ thống đọc tệp dữ liệu (hỗ trợ cả định dạng mảng JSON `[...]` và tệp CSV phân cách dấu phẩy).
  - Phân tích cú pháp schema: kiểm tra các trường bắt buộc, kiểu dữ liệu số, tính hợp lệ của lý trình `km_from <= km_to`.
  - Trả về danh sách mẫu 10 dòng đầu tiên, tổng số dòng hợp lệ và danh sách chi tiết các dòng lỗi (số dòng, tên trường, thông báo lỗi).
- **Giai đoạn 2: Thực thi Nhập liệu (`POST /api/datasets/{dataset}/import`)**
  - Thực hiện trong một Spring `@Transactional` duy nhất.
  - Toàn bộ các dòng hợp lệ được nạp vào `asset_record` với trạng thái mới nhất.
  - Nếu xuất hiện lỗi hệ thống hoặc vi phạm ràng buộc toàn vẹn, toàn bộ quá trình nhập bị hủy bỏ (Rollback) hoàn toàn, đảm bảo tính nhất quán (Atomicity).

---

## 3. MA TRẬN PHÂN QUYỀN VÀ TRÁCH NHIỆM (RBAC MATRIX)

| Hành động / Endpoint | ROLE_VIEWER | ROLE_EDITOR | ROLE_MANAGER | ROLE_ADMIN |
| :--- | :---: | :---: | :---: | :---: |
| **Xem danh sách / Chi tiết** (`GET /records`) | Cho phép | Cho phép | Cho phép | Cho phép |
| **Thêm mới tài sản** (`POST /records`) | Bị từ chối (403) | Cho phép | Cho phép | Cho phép |
| **Cập nhật tài sản** (`PUT /records/{id}`) | Bị từ chối (403) | Cho phép | Cho phép | Cho phép |
| **Xóa mềm tài sản** (`DELETE /records/{id}`) | Bị từ chối (403) | Bị từ chối (403) | Cho phép | Cho phép |
| **Kiểm thử Import (Dry-run)** (`POST /import/preview`) | Bị từ chối (403) | Bị từ chối (403) | Cho phép | Cho phép |
| **Thực thi Import Batch** (`POST /import`) | Bị từ chối (403) | Bị từ chối (403) | Cho phép | Cho phép |

---

## 4. HỆ QUẢ VÀ ĐÁNH ĐỔI (CONSEQUENCES)

### Tích Cực
- **Độ tin cậy cao:** Hoàn toàn tránh được hiện tượng mất dữ liệu (Data Loss) nhờ xóa mềm và bất biến tầng raw.
- **Bảo toàn dữ liệu đồng thời:** Khóa lạc quan triệt tiêu xung đột ghi đè dữ liệu ngầm (Lost Updates).
- **Trải nghiệm người dùng mượt mà:** Dynamic Form tự sinh theo schema tiết kiệm thời gian phát triển giao diện; Dry-run import giúp người dùng phát hiện lỗi tệp trước khi nạp vào hệ thống.
- **Tuân thủ chuẩn kiểm toán:** Mọi tương tác nghiệp vụ đều được ghi nhận đầy đủ trong `audit_log`.

### Hạn Chế & Lưu Ý Vận Hành
- **Dung lượng lưu trữ:** Việc lưu vết xóa mềm và giữ nguyên dữ liệu gốc ở tầng raw đòi hỏi dung lượng CSDL lớn hơn so với ghi đè trực tiếp. Cần có chính sách lưu trữ lâu dài (Archival Policy) sau chu kỳ 5-10 năm.
- **Chỉnh sửa GIS trong tương lai:** Việc cập nhật tọa độ hình học `geom` hiện đang được bảo vệ (khóa sửa đổi). Giai đoạn tiếp theo cần xây dựng giao diện vẽ bản đồ số hóa không gian (Spatial Digitizing / Geometry Editor) chuyên dụng với quy trình kiểm duyệt riêng.
