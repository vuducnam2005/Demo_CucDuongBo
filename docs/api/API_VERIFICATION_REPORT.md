# BÁO CÁO NGHIỆM THU VÀ HOÀN THIỆN BACKEND API NỀN TẢNG (GIAI ĐOẠN 4)

**Dự án:** Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)  
**Mô hình tham chiếu:** https://kcht.drvn.gov.vn/dashboard  
**Giai đoạn thực thi:** Giai đoạn 4 - Kiểm tra và hoàn thiện backend API nền tảng  
**Thời gian hoàn thành:** 06/10/2026  
**Trạng thái kiểm thử:** **50/50 Tests Passed (100%)**  

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC THỰC HIỆN

Căn cứ theo kế hoạch tổng thể tại `C:\Users\vuduc\Downloads\# Kế hoạch prompt xây dựng website.txt` và bộ quy tắc kiến trúc (Prompt 8, `AI_RULES.md`, `DATA_CONTRACT.md`, `API_CONVENTIONS.md`):
- **Không đập đi viết lại** các API đang hoạt động ổn định từ các giai đoạn trước.
- **Rà soát và đối chiếu hợp đồng API** đối với các phân hệ: Datasets, Records, Metadata, WebGIS GeoJSON, Reference Catalogs, Document Tree & File Stream.
- **Sửa lỗi và bổ sung các endpoint còn thiếu** theo chuẩn RESTful OpenAPI.
- **Bảo đảm phân trang xác định tuyệt đối (Deterministic Pagination):** Triệt tiêu hiện tượng trùng lặp hoặc nhảy cóc bản ghi giữa các ranh giới trang khi sắp xếp.
- **Khóa bảo mật môi trường sản xuất:** Tuyệt đối không cho phép người dùng ẩn danh tự động hưởng quyền `ROLE_ADMIN` ở cấu hình `prod`/`production`.
- **Bảo toàn dữ liệu 100%:** Toàn bộ 1.102.397 bản ghi gốc trong `raw_dataset_record` được giữ nguyên vẹn.

---

## 2. MA TRẬN ĐỐI CHIẾU VÀ HOÀN THIỆN CÁC ENDPOINT API

| STT | Phương thức | Đường dẫn API | Tầng xử lý | Mục đích & Trạng thái |
|:---:|:---:|:---|:---|:---|
| 1 | `GET` | `/api/datasets` | `DatasetQueryService` | Danh sách 658 dataset, phân trang, lọc theo `kind` |
| 2 | `GET` | `/api/datasets/{dataset}/metadata` | `DatasetQueryService` | Siêu dữ liệu, từ điển thuộc tính, kiểu dữ liệu, cờ lọc/tìm kiếm |
| 3 | `GET` | `/api/datasets/{dataset}/records` | `AssetQueryAdapter` (Curated + Raw) | Phân trang xác định, tìm kiếm `q`, lọc allowlist, sắp xếp an toàn |
| 4 | `GET` | `/api/datasets/{dataset}/records/{id}` | `AssetQueryAdapter` | Chi tiết bản ghi kèm payload JSON đầy đủ theo ID hoặc `record_key` |
| 5 | `GET` | `/api/datasets/{dataset}/geo` | `AssetQueryAdapter` + PostGIS | GeoJSON RFC 7946 FeatureCollection theo Bounding Box (BBOX) |
| 6 | `GET` | `/api/reference-catalogs/{catalog}` | `ReferenceCatalogQueryService` | Danh mục dùng chung (Tỉnh thành, cấp đường, biển báo...) |
| 7 | `GET` | `/api/catalogs/{catalog}` | `ReferenceCatalogApiController` | **[Bổ sung]** Alias URL ngắn gọn tương đương `/api/reference-catalogs` |
| 8 | `GET` | `/api/reference-catalogs/{catalog}/{itemCode}` | `ReferenceCatalogQueryService` | **[Bổ sung]** Tra cứu chi tiết một phần tử danh mục theo mã `itemCode` |
| 9 | `GET` | `/api/catalogs/{catalog}/{itemCode}` | `ReferenceCatalogApiController` | **[Bổ sung]** Alias tra cứu chi tiết một phần tử danh mục |
| 10 | `GET` | `/api/documents/folders` | `DocumentQueryService` | Cây thư mục hồ sơ tài liệu theo phân cấp đơn vị |
| 11 | `GET` | `/api/documents` | `DocumentQueryService` | Phân trang danh sách tài liệu, tìm kiếm `q`, lọc `folderId`, `mimeType` |
| 12 | `GET` | `/api/documents/{id}` | `DocumentQueryService` | Tra cứu chi tiết hồ sơ tài liệu theo ID hoặc `file_entry_id` |
| 13 | `GET` | `/api/documents/{id}/metadata` | `DocumentApiController` | **[Bổ sung]** Đường dẫn tường minh tra cứu metadata hồ sơ tài liệu |
| 14 | `GET` | `/api/documents/{id}/file` | `DocumentApiController` + Storage | **[Bổ sung]** Tải tệp nhị phân kèm header `Content-Disposition: attachment` |
| 15 | `GET` | `/actuator/health` | Spring Boot Actuator | Kiểm tra trạng thái hệ thống, CSDL PostgreSQL/PostGIS và Disk space |

---

## 3. CÁC NÂNG CẤP KỸ THUẬT QUAN TRỌNG

### 3.1. Đảm bảo tính phân trang xác định (Deterministic Pagination Invariance)
- **Vấn đề tiềm ẩn:** Trong PostgreSQL, khi câu truy vấn có mệnh đề `ORDER BY col` (ví dụ `route_code` hoặc `name`) mà các dòng có cùng giá trị, thứ tự trả về giữa các câu truy vấn phân trang `LIMIT size OFFSET offset` có thể không cố định, dẫn đến hiện tượng bản ghi ở trang 0 bị lặp lại ở trang 1, hoặc bản ghi bị nhảy cóc (skipped).
- **Giải pháp áp dụng:**
  1. Trong `DatasetQueryService.parseSafeSortClause`: Mọi mệnh đề sắp xếp ngoài cột `id` đều tự động được bổ sung khóa phụ `, id ASC`.
     ```sql
     ORDER BY raw_payload->>'route_code' ASC NULLS LAST, id ASC
     ```
  2. Trong `AssetQueryAdapterImpl.parseSortParam`: Mọi tiêu chí sắp xếp của Spring Data JPA đều được nối tiếp thêm `.and(Sort.by(Sort.Direction.ASC, "id"))`.
- **Kiểm chứng:** Test case `testDeterministicPagination` trong `DatasetApiContractIntegrationTest` xác nhận tập hợp ID giữa trang 0 và trang 1 là hoàn toàn rời nhau (zero disjoint overlap).

### 3.2. Kiểm soát an ninh môi trường sản xuất (Production Profile Security)
- **Cơ chế:** Trong `DatasetPermissionService`, bổ sung biến cấu hình `@Value("${spring.profiles.active:dev}")` và `@Value("${kcht.security.allow-anonymous-admin:true}")`.
- **Hành vi kiểm soát:**
  - Ở profile `dev`/`local`: Yêu cầu không có header `X-User-Role` sẽ mặc định là `ROLE_ADMIN` để hỗ trợ lập trình viên kiểm thử nhanh chóng.
  - Ở profile `prod`/`production` hoặc khi tắt `allow-anonymous-admin`: Yêu cầu ẩn danh **tự động bị hạ cấp về `ROLE_VIEWER`**. Nếu truy cập các dataset quản trị (`audit_log`, `app_user`, `import_job`, hoặc phân hệ bảo trì chi tiết `maintenance_detail_*`), hệ thống lập tức trả về `403 Forbidden` (`AccessDeniedException`).
- **Kiểm chứng:** Test suite `DatasetPermissionServiceTest` kiểm thử đầy đủ 5 kịch bản phân quyền và bảo vệ an toàn.

### 3.3. Hỗ trợ đa dạng định dạng hồ sơ và tải tệp nhị phân
- Trong `DocumentQueryService`, phương thức `downloadFileContent` thực hiện:
  - Tra cứu metadata tài liệu theo ID hoặc UUID `file_entry_id`.
  - Tải nội dung nhị phân từ Object Storage (hoặc Local File Storage).
  - Nếu tệp gốc chưa hoàn tất đồng bộ từ máy chủ nguồn, tự động khởi tạo văn bản thông tin hồ sơ chuẩn hóa kèm định dạng UTF-8 để người dùng kiểm tra thông tin.
  - Tự động gắn tiêu đề `Content-Disposition: attachment; filename="<tên_tệp>"` và `Content-Type` chuẩn MIME (`application/pdf`, `image/png`, v.v.).

---

## 4. KẾT QUẢ THỰC THI KIỂM THỬ TOÀN DIỆN (TEST EXECUTION SUITE)

Đã chạy toàn bộ test suite dự án bằng lệnh `.\mvnw.bat test`. Tất cả **50 bài test** đều vượt qua tuyệt đối (0 failures, 0 errors, 0 skipped):

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  24.612 s
[INFO] Finished at: 2026-10-06T00:21:35+07:00
[INFO] ------------------------------------------------------------------------
```

### Chi tiết các lớp kiểm thử:
1. `vn.gov.drvn.kcht.controller.DatasetApiContractIntegrationTest` (5 tests):
   - `testCatalogAliasRoute`: Kiểm tra alias `/api/catalogs/{catalog}` -> Passed.
   - `testCatalogItemLookup`: Tra cứu chi tiết item danh mục (Hà Nội "01") và xử lý 404 -> Passed.
   - `testDocumentDetailLookup`: Tra cứu metadata tài liệu theo ID & UUID -> Passed.
   - `testDocumentFileDownload`: Tải tệp nhị phân kèm Content-Disposition attachment -> Passed.
   - `testDeterministicPagination`: Kiểm chứng phân trang xác định tuyệt đối không trùng lặp -> Passed.
2. `vn.gov.drvn.kcht.service.DatasetPermissionServiceTest` (5 tests):
   - `testDevAnonymousAdmin`: Kiểm tra bypass thuận tiện ở profile dev -> Passed.
   - `testProdAnonymousBlockedForSensitiveDatasets`: Khóa chặt quyền ở profile prod -> Passed.
   - `testAdminFullAccess`: Toàn quyền truy cập cho ROLE_ADMIN -> Passed.
   - `testViewerRestrictedAccess`: ROLE_VIEWER bị chặn bảng quản trị và bảo trì chi tiết -> Passed.
   - `testOperatorAccess`: ROLE_OPERATOR được xem bảo trì, chặn tài khoản/kiểm toán -> Passed.
3. `vn.gov.drvn.kcht.controller.DatasetApiPostgisIntegrationTest` (10 tests):
   - Tra cứu dataset, metadata, bản ghi, WebGIS GeoJSON BBOX, allowlist SQL injection defense -> 10/10 Passed.
4. `vn.gov.drvn.kcht.DatabaseHandoffIntegrationTest` (10 tests):
   - Kiểm tra 8 JPA entities, 9 repositories, spatial projection và audit log -> 10/10 Passed.
5. `vn.gov.drvn.kcht.controller.DatasetImportSmokeIntegrationTest` (6 tests):
   - Đọc qua cầu nối AssetQueryAdapter, bất biến dữ liệu gốc 1.102.397 dòng -> 6/6 Passed.
6. `vn.gov.drvn.kcht.controller.ActuatorHealthIntegrationTest` (2 tests):
   - Health probes UP cho CSDL PostGIS và disk -> 2/2 Passed.
7. `vn.gov.drvn.kcht.service.*` (12 tests):
   - Rollback, dry-run, payload validation, record key resolution -> 12/12 Passed.

---

## 5. ĐÁNH GIÁ TIÊU CHÍ HOÀN THÀNH (DEFINITION OF DONE)

- [x] **Rà soát hợp đồng API:** Toàn bộ các endpoint của Prompt 8 và yêu cầu bàn giao CSDL đã được rà soát, bổ sung và chuẩn hóa.
- [x] **Không phá vỡ cấu trúc cũ:** Giữ nguyên các controller và service đang chạy ổn định; chỉ mở rộng và gia cố tính năng.
- [x] **Phân trang ổn định (Deterministic):** Đã bổ sung tie-breaker `id ASC` và có bài test kiểm chứng.
- [x] **An ninh môi trường sản xuất:** Khóa chặt quyền ẩn danh trong `prod`/`production`.
- [x] **Tài liệu đặc tả:** Cập nhật hoàn chỉnh `docs/api/REST_API_SPECIFICATION.md` và `docs/api/API_VERIFICATION_REPORT.md`.
- [x] **Kiểm thử tự động:** Đạt 100% tỷ lệ đỗ với 50/50 test cases.
- [x] **Dữ liệu nguyên vẹn:** Toàn bộ 1.102.397 bản ghi trong CSDL PostgreSQL/PostGIS được bảo toàn nguyên trạng.

**Kết luận:** Giai đoạn 4 đã hoàn thành xuất sắc và sẵn sàng cho các giai đoạn tiếp theo theo lộ trình.
