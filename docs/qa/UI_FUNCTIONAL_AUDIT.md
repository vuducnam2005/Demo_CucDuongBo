# Kiểm toán chức năng UI - Giai đoạn 0

Ngày kiểm tra: 2026-10-07 (Asia/Saigon)  
Phạm vi: `C:\Demo_CucDuongBo`, frontend `http://localhost:3000`, backend `http://localhost:8089`; Playwright Chromium viewport `1366x768`, responsive component checks tương đương mobile hẹp.

## Nguyên tắc và môi trường

- Đã đọc quy tắc dự án, kiến trúc, API/data contract, Definition of Done, Test Matrix, Flyway `V1`-`V13` và tài liệu trong `docs` trước khi sửa.
- Thư mục không phải Git repository; không chạy `git init`, không reset database/schema và không chạy lại import dữ liệu.
- PostgreSQL/PostGIS, MinIO, backend và Vite đang chạy trên dữ liệu thật. Không dùng mock để nghiệm thu route thực tế.
- Kiểm tra quyền bằng quick-login có sẵn của dự án: ADMIN cho route quản trị; VIEW để xác nhận trang 403. Không bypass CAPTCHA, đăng nhập hay RBAC.
- Không ghi token, cookie, mật khẩu hoặc chuỗi kết nối vào hồ sơ này.

## Kết quả theo route

| Route | Request chính và status thực tế | Response shape đã quan sát/đối chiếu | Loading / empty / error | Filter / sort / phân trang | Quyền và tiếng Việt | Độ khớp UI - API - DB | Kết luận |
|---|---|---|---|---|---|---|---|
| `/dashboard` | `GET /api/dashboard/summary`, `/stats/branches`, `/stats/datasets`, `/stats/road-signs`, `/stats/road-lengths`, `/stats/recent-assets?limit=10` -> `200` | Summary object; các bảng thống kê là array; road-sign/road-length là object aggregate | Có skeleton/loading và nút làm mới; dữ liệu thật hiển thị; chưa ép lỗi API trong live run | Sort bảng chi nhánh ở client; bảng chi nhánh phân trang client 10 dòng; KPI không phân trang | Người dùng đã xác thực truy cập được. Nhãn UI và họ tên tài khoản, lời chào tiếng Việt chuẩn UTF-8 sau Flyway V10 | Tổng `830.836`, cầu `11.631`, biển báo `222.112`, `658` tập dữ liệu khớp response/database; họ tên khớp hoàn toàn `Quản trị viên Hệ thống` | PASS P0.6 / PARTIAL P1 |
| `/assets` | `GET /api/datasets/tree`, `GET /api/datasets/{key}/metadata`, `GET /api/datasets/{key}/records?page=0&size=20&q=&sort=id,asc...` -> `200` | Tree array; metadata object; records `PagedResponse<RecordItem>` | Loading chuyển dataset hoạt động; có empty/error state và refresh; mobile chuyển card không ép bảng rộng | Tìm kiếm, `filter_branch_id`, sort và pagination đều gửi server; action column fixed-right trên desktop | VIEW được đọc; thao tác ghi phụ thuộc RBAC. Tiếng Việt UI đúng, dữ liệu seed chuẩn UTF-8 | Số lượng và dòng dữ liệu khớp API/PostgreSQL; null/thiếu mapping và trạng thái raw hiển thị riêng | PASS P1.001 (desktop/mobile/contract) |
| `/map` | `GET /api/datasets/{dataset}/clusters` hoặc `/geo` với `minLon,minLat,maxLon,maxLat`, zoom, filter, limit -> `200`/`403`/`400` theo contract | GeoJSON `FeatureCollection`; geometry hợp lệ là `Point`, tọa độ WGS84/EPSG:4326; limit geo tối đa 2.000 | Có loading/empty/error/retry theo lớp; request cũ bị hủy; abort không tạo toast; lỗi được throttle theo lớp/lỗi | Query server theo bbox; cluster tối đa 500; geo tối đa 500 từ UI; road-sign BBOX/cluster đọc projection cache hẹp | RBAC kiểm bằng quyền thật; tiếng Việt UI đúng | Integration 10 GIS tests pass; benchmark road-sign thật BBOX `89ms`, cluster `66ms`; cache `55.612/55.612` source points | PASS P0.5/P1.002 |
| `/reports` | `GET /api/reports/road-lengths`, `/maintenance`, `/road-signs-blackspots` -> `200`; export cả 3 -> `200 text/csv` | DTO giữ đúng tên backend: `averageLengthKm`, `percent`, `completedCount`, `completionRatePercent`, `totalSignCategories`, `signPrefix`, `sampleSignCode`, `kmMarker`, `incidentCount`, `fatalityCount` | Có loading/error state theo query; empty filter trả aggregate zero có chủ ý; live run render thành công | Filter gửi server; bảng chi tiết phân trang client; export giữ filter | Người dùng đã xác thực truy cập được; nhãn và số format `vi-VN`; null tọa độ không bị đổi thành 0 | SQL nguồn: `168` route có chiều dài, tổng `27.469,255 km`, average `163,507... km`; API làm tròn hiển thị `27.469,3`, `163,5`; branch `cdb_vn` `100,0%`; response và UI khớp | PASS P0.3 |
| `/documents` | `GET /api/documents/folders`, `GET /api/documents?folderId=&search=&extension=&page=0&size=15` -> `200` | Folder array; document page gồm `content/page/size/totalElements/totalPages` | Loading cây thư mục và bảng hoạt động; có empty/error state | Search, extension, folder và pagination gửi server; live run có 121 folder | Người dùng đã xác thực truy cập được; thao tác upload/delete do RBAC API quyết định; nhãn tiếng Việt đúng | Metadata và số lượng folder/document khớp API | PASS |
| `/catalogs` | `GET /api/catalogs`, `GET /api/catalogs/{code}?page=0&size=15&keyword=` -> `200` | Catalog summary array; item page object | Có loading, empty, error cho danh sách/item | Danh sách 153 catalog lọc client; item keyword và pagination gửi server; sort hiển thị theo `sortOrder` | Người dùng đã xác thực truy cập được; CRUD phụ thuộc API/RBAC; tiếng Việt đúng | Danh mục và item khớp response/database | PASS |
| `/admin/users` | Trước sửa: `GET /api/auth/users?page=0&size=10` -> `200` nhưng UI crash. Sau sửa: cùng endpoint, thêm `q`, -> `200` | Chuẩn `PagedResponse<UserSummary>`: `content/page/size/totalElements/totalPages/first/last`; item có `active`; POST trả `UserSummary` không có password | Trước sửa ErrorBoundary nhận `rawData.some is not a function`. Sau sửa: skeleton lần đầu, spinner khi đổi trang, empty/error riêng cho 401/403/5xx; modal giữ input khi POST lỗi | Search `q` toàn DB theo username/fullName/email/role; sort cố định `id ASC`; pagination server. Live: trang 1 `1-10/26`, mật khẩu yếu hiển thị validation và giữ modal, mật khẩu hợp lệ POST 201 đóng modal/refresh | ADMIN truy cập; VIEW nhận trang 403 ở cả UI và API. Nút role hiển thị `Phân quyền (chưa hỗ trợ)` và disabled; họ tên và vai trò chuẩn UTF-8 sau V10 | Contract UI/API đã đồng bộ; bảng dùng `response.content`; không còn crash; tạo user có request thật; tiếng Việt chuẩn | PASS P0.1/P0.2/P0.6 |
| `/admin/audit-logs` | `GET /api/audit-logs?page=0&size=10&username=` -> `200` | `PagedResponse<AuditLogItem>` | Loading, empty, error và modal chi tiết hoạt động | Username filter và pagination gửi server; live `10 / 758`; chưa có sort UI | ADMIN truy cập; VIEW nhận trang 403; nhãn tiếng Việt đúng | Dữ liệu bảng khớp response/audit table | PASS |

## Bằng chứng P0.1 trước và sau sửa

### Trước sửa

- Client khai báo `fetchUsers(): Promise<UserSummary[]>` nhưng backend trả đối tượng phân trang.
- `UserManagementPage` gọi `.filter()` trên toàn response, sau đó Ant Table nhận object thay vì array.
- Live browser tái hiện `TypeError: rawData.some is not a function`; toàn trang bị ErrorBoundary thay thế.
- Search chỉ lọc dữ liệu đang có ở client, nên không đại diện toàn bộ tài khoản.
- Modal tạo người dùng báo thành công nhưng không gọi `POST /api/auth/users`.

### Sau sửa

- `fetchUsers({page,size,q})` trả `PagedResponse<UserSummary>` và kiểm tra runtime bằng utility generic nhận `unknown` cùng type guard.
- Table nhận duy nhất `response.content`; query key chứa page, size và từ khóa.
- Backend tìm toàn hệ thống theo username, full name, email, role code và role name.
- 401/403 không retry; lỗi network/5xx retry đúng một lần; lỗi hiển thị nội dung phù hợp.
- Modal tạo tài khoản gọi API thật, chỉ đóng/reset sau HTTP success; khi lỗi giữ nguyên dữ liệu đã nhập.
- Trạng thái tài khoản lấy từ trường `active` thật, không còn hiển thị "Hoạt động" cố định.

## Kiểm thử đã chạy

| Lệnh / kiểm tra | Kết quả |
|---|---|
| Baseline `\.\mvnw.bat test` trước sửa | 108 passed, 0 failed |
| Baseline `npm test` trước sửa | 36 passed, 3 failed (2 RouteGuard, 1 Phase11Crud) |
| `\.\mvnw.bat -Dtest=RbacAuthorizationIntegrationTest test` sau sửa | 6 passed, 0 failed |
| `npm test -- --run src/test/UserManagementPage.test.tsx` | 12 passed, 0 failed |
| `\.\mvnw.bat -Dtest=PerformanceBenchmarkIntegrationTest test` chạy riêng | 6 passed; keyword-search p95 `86 ms`; GIS benchmark hoàn tất dưới 60 giây sau V12/V13 |
| `\.\mvnw.bat -Dtest=Phase10IntegrationTest test` | 11 passed, 0 failed; contract/SQL/filter/CSV/null cases |
| `npm test -- --run src/test/Phase10Pages.test.tsx` | 5 passed, 0 failed; backend field names, zero/null/large/vi-VN rendering |
| PowerShell wrapper nạp biến môi trường từ `backups/local-secrets/.env.local` rồi `\.\mvnw.bat test` | 122 tests; 122 passed; `BUILD SUCCESS` |
| `npm test -- --run` cuối vòng sau bổ sung P1 hồi quy | 81 passed, 0 failed (13 suites) |
| `npm run test:e2e` cuối vòng | 8 passed, 0 failed trên Chromium; fixture đồng bộ `first/last`, `recordStatus`, metadata fields |
| `npm run build` cuối vòng | TypeScript + Vite build thành công; còn cảnh báo chunk Ant Design lớn |
| Live ADMIN `/admin/users` | Không crash; search và chuyển trang server-side đúng |
| Live ADMIN tạo người dùng | Mật khẩu không đạt policy hiển thị lỗi backend và giữ modal; mật khẩu hợp lệ gửi POST thật, nhận 201, hiện toast, đóng modal và refresh danh sách; response UI không hiển thị mật khẩu |
| Live VIEW `/admin/users`, `/admin/audit-logs` | Trang 403 đúng; sau đó đã khôi phục phiên ADMIN |

Các cổng P0 cuối vòng pass. Benchmark keyword đạt p95 `86 ms` trong full Maven run sau V12/V13. Các prop Ant Design đã được cập nhật theo API hiện hành; chỉ còn cảnh báo React Router/`act` trong test harness cần theo dõi, không ảnh hưởng luồng đã kiểm chứng.

## Kiểm kê file đã thay đổi

- Backend: `src/main/java/vn/gov/drvn/kcht/repository/AppUserRepository.java`, `src/main/java/vn/gov/drvn/kcht/controller/AuthApiController.java`, `src/main/java/vn/gov/drvn/kcht/service/AuthService.java`, `src/main/java/vn/gov/drvn/kcht/dto/UserSummaryDto.java`, `src/main/java/vn/gov/drvn/kcht/dto/CreateUserRequestDto.java`.
- Backend observability/fallback hardening: `src/main/java/vn/gov/drvn/kcht/adapter/AssetQueryAdapterImpl.java`, `src/main/java/vn/gov/drvn/kcht/mapper/ReferenceCatalogMapper.java`, `src/main/java/vn/gov/drvn/kcht/service/AssetCrudService.java`, `src/main/java/vn/gov/drvn/kcht/service/DatasetRegistryService.java`, `src/main/java/vn/gov/drvn/kcht/service/DocumentQueryService.java`, `src/main/java/vn/gov/drvn/kcht/service/PayloadValidator.java`, `src/main/java/vn/gov/drvn/kcht/service/ReferenceCatalogQueryService.java`.
- Backend test cleanup observability: `src/test/java/vn/gov/drvn/kcht/DatabaseHandoffIntegrationTest.java`.
- Backend test: `src/test/java/vn/gov/drvn/kcht/controller/RbacAuthorizationIntegrationTest.java`.
- Frontend: `frontend/src/services/pagination.ts` (mới), `frontend/src/services/api.ts`, `frontend/src/pages/UserManagementPage.tsx`.
- Frontend P0.2: `frontend/src/pages/AssetListPage.tsx`, `frontend/src/layouts/MainLayout.tsx`.
- Frontend test/E2E: `frontend/src/test/UserManagementPage.test.tsx` (mới), `frontend/src/test/RouteGuard.test.tsx`, `frontend/src/test/Phase11Crud.test.tsx`, `frontend/e2e/kcht-e2e.spec.ts`.
- Hồ sơ QA: `docs/qa/UI_FUNCTIONAL_AUDIT.md`, `docs/qa/BUG_MATRIX.md`, `docs/qa/ROUTE_ACCEPTANCE_MATRIX.md`.
- Artefact được tái sinh khi xác minh: `frontend/dist/*`; fixture do integration test tạo: `data/storage/documents/doc_bc9ac10c6b624c038dc58b8c51716e35.pdf`.

## Giới hạn và dữ liệu cần bảo vệ

- Không chỉnh sửa migration cũ. Lỗi tiếng Việt cần một forward migration mới sau khi xác định chính xác mapping dữ liệu gốc.
- Không xóa các tài khoản test đã tồn tại. Test tích hợp RBAC hiện tạo tài khoản thật và không rollback; đây là lỗi test isolation được ghi trong `BUG_MATRIX.md`.
- Không xóa/reset database hay chạy lại import. Fixture file do test backend tạo, gồm `documents/doc_bc9ac10c6b624c038dc58b8c51716e35.pdf` ở lần chạy cuối, được giữ nguyên vì có thể đang được bản ghi kiểm thử tham chiếu.

## Bằng chứng P0.3 - báo cáo và đối chiếu SQL

- SQL trên PostgreSQL với `dataset_key = 'mst_national_road'` và `column_identify = 'actual_length'` trả `168` dòng, tổng `27469.255` km, average `163.507470...` km.
- API sau sửa trả `totalRoutes=168`, `totalLengthKm=27469.3`, `averageLengthKm=163.5`; không còn cộng record kiểm thử thiếu `actual_length` (trước đó bị gán giả `50.0` km).
- Phân bổ branch SQL/API: `cdb_vn=160 / 27469.3 / 100.0%`, `sxd_vl=7 / 0.0 / 0.0%`, `sxd_bn=1 / 0.0 / 0.0%`.
- Filter `branch=cdb_vn` được kiểm thử tích hợp với cùng điều kiện SQL và trả đúng `160` tuyến; filter không tồn tại trả tổng tuyến/chiều dài/trung bình bằng `0`, không sinh dữ liệu giả.
- JSON contract không còn alias frontend cũ (`avgLengthKm`, `percentage`, `completedProjects`, `signCategoryCount`, `locationKm`, ...); frontend đọc trực tiếp field DTO backend.
- CSV road length/maintenance/signs có UTF-8 BOM, header tiếng Việt và dữ liệu theo filter; trường tọa độ null xuất rỗng, không đổi thành số 0.

## Kiểm kê file P0.3 đã thay đổi

- `src/main/java/vn/gov/drvn/kcht/service/ReportService.java`
- `src/test/java/vn/gov/drvn/kcht/controller/Phase10IntegrationTest.java`
- `frontend/src/services/reportApi.ts`
- `frontend/src/pages/ReportIndexPage.tsx`
- `frontend/src/test/Phase10Pages.test.tsx`
- `docs/qa/BUG_MATRIX.md`
- `docs/qa/UI_FUNCTIONAL_AUDIT.md`
- `docs/qa/ROUTE_ACCEPTANCE_MATRIX.md`

## Kiểm kê file P0.4/P0.5 đã thay đổi

- `frontend/src/components/common/ErrorBoundary.tsx`
- `frontend/src/services/clientLogger.ts`
- `frontend/src/services/gisApi.ts`
- `frontend/src/services/api.ts` (lọc thông báo lỗi backend trước khi hiển thị)
- `frontend/src/pages/CatalogListPage.tsx`
- `frontend/src/pages/DocumentExplorerPage.tsx`
- `frontend/src/components/assets/AssetFormModal.tsx`
- `frontend/src/components/assets/AssetImportModal.tsx`
- `frontend/src/pages/AssetListPage.tsx`
- `frontend/src/pages/LoginPage.tsx`
- `frontend/src/pages/WebGisPage.tsx`
- `frontend/src/vite-env.d.ts`
- `frontend/src/test/CommonComponents.test.tsx`
- `frontend/src/test/WebGisPage.test.tsx`
- `frontend/src/test/gisApi.test.ts`
- `src/main/java/vn/gov/drvn/kcht/service/DatasetQueryService.java`
- `src/test/java/vn/gov/drvn/kcht/controller/GisApiIntegrationTest.java`
- `docs/qa/BUG_MATRIX.md`
- `docs/qa/UI_FUNCTIONAL_AUDIT.md`
- `docs/qa/ROUTE_ACCEPTANCE_MATRIX.md`

## Bằng chứng P0.4/P0.5

- ErrorBoundary production test không render message nội bộ, `node_modules`, đường dẫn source hoặc component stack; chỉ render mã lỗi `ui-*`, nút tải lại và về dashboard. Development vẫn giữ chi tiết để debug.
- `WebGisPage` dùng `AbortController`, request key gồm dataset/bbox/zoom/filter/chế độ cluster, không chạy trùng request đang chờ; response rỗng chuyển sang empty state hợp lệ; lỗi một lớp không thay đổi lớp khác.
- Geometry trước khi đưa vào OpenLayers phải là GeoJSON `Feature` kiểu `Point` với lon/lat hữu hạn trong miền WGS84; geometry null/không hợp lệ bị bỏ qua an toàn.
- `gisApi` truyền `AbortSignal` và timeout riêng 45 giây cho endpoint không gian; không tải toàn bộ dataset lớn vào browser.
- Backend integration: `GisApiIntegrationTest` 10/10 (bao gồm lớp đường `tbl_km_post` và `mst_national_road`) và `DatasetApiPostgisIntegrationTest` 10/10; frontend targeted 33/33 và full suite 81/81 (13 suites).

## Bằng chứng P0.6 - Tiếng Việt UTF-8 và Profile Cache

- Flyway forward migration `V10__fix_vietnamese_encoding_auth.sql` cập nhật các bản ghi hạt giống `app_user` và `app_role` về đúng tiếng Việt UTF-8 nguyên bản.
- Backup bảng auth tạo trước khi chạy migration tại `backups/backup_auth_tables_before_v10.sql`.
- SQL assertion sau migration: `SELECT count(*) FROM app_user WHERE full_name LIKE '%?%'` = 0; `SELECT count(*) FROM app_role WHERE role_name LIKE '%?%' OR description LIKE '%?%'` = 0.
- `application.yml` được bổ sung `server.servlet.encoding.charset=UTF-8`, `force=true`, `force-response=true`.
- `WebConfig.java` đăng ký bean `CharacterEncodingFilter` đảm bảo mọi HTTP request/response đều có encoding UTF-8 có hiệu lực.
- `frontend/src/context/AuthContext.tsx` bổ sung cơ chế kiểm soát cache phiên bản `v2_utf8` và hàm `isProfileValid`. Nếu cache cũ chứa dấu `?` hỏng (như `Qu???n tr??? vi??n`), hệ thống tự động loại bỏ cache và làm mới từ `/api/auth/me`.
- Tuyệt đối không dùng kỹ thuật thay thế `?` giả tạo ở frontend; dữ liệu được bảo toàn chuẩn từ PostgreSQL qua Spring Boot đến React.
- Test hồi quy backend: `VietnameseEncodingIntegrationTest` (3 tests) kiểm thử xác thực database, API `/api/auth/users` và các từ khóa chuẩn: `Quản trị viên`, `Hệ thống`, `Đường quốc lộ`, `Cầu đường bộ`, `Tỉnh/thành phố`.
- Test hồi quy frontend: `VietnameseEncoding.test.tsx` (5 tests) kiểm thử phát hiện profile hỏng, cơ chế làm mới cache, hiển thị lời chào `Xin chào, Quản trị viên Hệ thống.` và các từ khóa KCHT tiếng Việt.
- Build và E2E: Full Vitest `81/81 passed` (13 suites); `tsc -b && vite build` thành công; Playwright E2E `8/8 passed`.

## Kiểm kê file P2 đã thay đổi

- `src/main/resources/application.yml` (bổ sung cấu hình `kcht.security.cookie`)
- `src/main/resources/application-prod.yml` (yêu cầu biến môi trường bắt buộc, loại bỏ default secrets, bật `secure=true`)
- `src/main/resources/application-test.yml` (bổ sung cấu hình cookie an toàn cho test)
- `src/main/java/vn/gov/drvn/kcht/controller/AuthApiController.java` (inject thuộc tính cookie qua `@Value`, cấu hình `SameSite`, `Secure`, `Path`, `Domain`, TTL)
- `src/test/java/vn/gov/drvn/kcht/controller/AuthCookieSecurityTest.java` (test mới kiểm thử cookie attributes: dev HTTP vs prod HTTPS)
- `docker-compose.yml` (loại bỏ default password của MinIO trong docker compose)
- `docker-compose.prod.yml` (bắt buộc biến môi trường cho Flyway, Postgres, MinIO và Backend, cấu hình `REFRESH_COOKIE_SECURE=true`)
- `db/migration/V12__keyword_search_trigram_indexes.sql` và `db/migration/V13__bridge_keyword_search_composite_index.sql` (tối ưu truy vấn keyword theo đúng contract API)
- `src/main/java/vn/gov/drvn/kcht/service/DatasetQueryService.java` (dùng predicate composite có index cho `tbl_bridge`)
- `src/test/java/vn/gov/drvn/kcht/benchmark/PerformanceBenchmarkIntegrationTest.java` (giữ benchmark chạy PostgreSQL thật, không ép profile H2)
- `.env.example` (bổ sung placeholder vô hại cho JWT và cookie)
- `.dockerignore` (loại trừ `.env`, khóa riêng `*.key`, `*.pem`, `*.dump`)
- `frontend/.dockerignore` (loại trừ `.env`, `*.key`)
- `.gitignore` (ngăn chặn secrets, file môi trường, certificate và dump đưa vào Git)
- `docs/qa/BUG_MATRIX.md`
- `docs/qa/ROUTE_ACCEPTANCE_MATRIX.md`
- `docs/qa/UI_FUNCTIONAL_AUDIT.md`

## Bằng chứng P2 (Bảo mật cấu hình & Chất lượng TypeScript)

- **P2.1 - Bảo mật cấu hình & Secrets:**
  - `application-prod.yml` bắt buộc cấu hình các biến môi trường: `${JWT_SECRET}`, `${POSTGRES_PASSWORD}`, `${MINIO_ROOT_USER}`, `${MINIO_ROOT_PASSWORD}`. Nếu không cung cấp, ứng dụng trên profile `prod` sẽ từ chối khởi động thay vì dùng secret mặc định.
  - Cookie refresh HttpOnly được cấu hình động qua môi trường: `kcht.security.cookie.secure`, `same-site`, `domain`, `path`, và TTL đồng bộ với `kcht.security.jwt.refresh-token-expiration-ms`; profile production yêu cầu truyền đủ biến và `secure=true`.
  - Kiểm thử `AuthCookieSecurityTest` (2 tests) xác nhận cookie dev (không có `Secure;`) và cookie prod (bắt buộc có `Secure;`, `SameSite=Strict`, `Domain=drvn.gov.vn`).
  - `.dockerignore` và `.gitignore` ngăn chặn triệt để tệp `.env`, `.env.*`, khóa TLS riêng (`deploy/nginx/ssl/*.key`), chứng chỉ và file dump đưa vào image Docker hoặc Git.
  - `.env` cục bộ đã được chuyển khỏi workspace source sang `backups/local-secrets/.env.local` (thư mục bị ignore); mọi credential từng tồn tại phải được rotate trước khi dùng staging/production.
  - `docker compose -f docker-compose.prod.yml config` không có biến bắt buộc dừng ngay với lỗi thiếu `MINIO_ROOT_USER`, không dùng fallback.
- **P2.2 - Chất lượng TypeScript & Build:**
  - Không có bất kỳ ép kiểu `as any` nào trên toàn bộ đường dữ liệu chính của frontend (`@typescript-eslint/no-explicit-any: error`).
  - `tsconfig.json` duy trì `strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`.
  - `npm run lint` đạt 0 warnings, 0 errors.
  - `npm run build` (`tsc -b && vite build`) hoàn tất xuất sắc với 7 vendor chunks tách biệt.
  - `npm test` đạt 81/81 tests passed (13 suites).
  - Playwright E2E đạt 8/8 scenarios passed.

