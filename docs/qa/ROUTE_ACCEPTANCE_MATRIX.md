# Ma trận nghiệm thu route - Giai đoạn 0

Quy ước: `PASS` đạt; `PARTIAL` hoạt động nhưng còn lỗi đã định danh; `FAIL` sai dữ liệu/chức năng; `N/A` không áp dụng.

| Route | HTTP/API | Shape/contract | Loading | Empty | Error | Filter/search | Sort | Pagination | RBAC | Tiếng Việt | UI so với DB | Tổng |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/dashboard` | PASS | PASS | PASS | PARTIAL | PARTIAL | N/A | PASS client | PASS client | PASS | PASS tiếng Việt | PASS số liệu chính | PASS P0.6 / PARTIAL P1 |
| `/assets` | PASS | PASS | PASS | PASS | PASS | PASS server | PASS server | PASS server | PASS read/write theo role | PASS UI | PASS; desktop sticky action/mobile card | PASS P1.001 |
| `/map` | PASS P0.5/P1.002 | PASS GeoJSON/EPSG:4326 | PASS per-layer | PASS empty hợp lệ | PASS per-layer/retry/throttle | PASS server bbox/filter/cache | N/A | N/A, dùng cluster/BBOX | PASS | PASS | PASS; road-sign cluster `66ms`, BBOX `89ms` | PASS P0.5/P1.002 |
| `/reports` | PASS | PASS DTO backend | PASS | PASS (zero aggregate có chủ ý) | PASS | PASS server | PARTIAL/client | PASS client | PASS | PASS `vi-VN`/null | PASS sau đối chiếu SQL | PASS P0.3 |
| `/documents` | PASS | PASS | PASS | PASS | PASS | PASS server | PARTIAL | PASS server | PASS | PASS | PASS | PASS |
| `/catalogs` | PASS | PASS | PASS | PASS | PASS | PASS item server/catalog client | PASS `sortOrder` | PASS server | PASS read | PASS | PASS | PASS |
| `/admin/users` | PASS | PASS | PASS | PASS | PASS | PASS server | PASS cố định `id ASC` | PASS server | PASS: ADMIN 200, VIEW 403; role button disabled trung thực | PASS UTF-8 V10 | PASS contract/dòng; POST create thật | PASS P0.1/P0.2/P0.6 |
| `/admin/audit-logs` | PASS | PASS | PASS | PASS | PASS | PASS server | N/A | PASS server | PASS: ADMIN 200, VIEW 403 | PASS UI | PASS | PASS |

## Acceptance P0.1 chi tiết

| Tiêu chí | Bằng chứng | Kết quả |
|---|---|---|
| `GET /api/auth/users` trả page chuẩn | Integration test kiểm `content/page/size/totalElements/totalPages/first/last` | PASS |
| Frontend không coi page object là array | Utility từ chối array; component render `data.content` | PASS |
| Pagination server-side | Live trang 1 `1-10/21`, trang 2 `11-20/21`; query test page 0/1 | PASS |
| Search toàn hệ thống | Live `q=viewer_demo` -> 1/1; backend test username và role | PASS |
| Empty | Backend random q -> empty page; component empty state | PASS |
| 401 | Backend anonymous 401; component không retry và hướng dẫn đăng nhập lại | PASS |
| 403 | Backend VIEW 403; live UI 403; component không retry | PASS |
| 5xx/network retry | Component test xác nhận retry đúng một lần | PASS |
| Loading khi đổi trang | Table nhận `isFetching`; live không mất shell/table | PASS |
| Create chỉ success sau API thật | `createUser` mutation; test failure giữ form | PASS |
| Trạng thái tài khoản không giả | DTO trả `active`; UI render Hoạt động/Đã khóa theo response | PASS |
| Tạo tài khoản chỉ success sau API | Live mật khẩu yếu -> validation/giữ modal; live hợp lệ -> POST 201, toast, đóng modal, refresh | PASS |
| Chống submit lặp | Component test giữ mutation pending và xác nhận chỉ một lần gọi `createUser` | PASS |
| Không báo thành công giả cho phân quyền | Chưa có endpoint role update; nút disabled và nhãn `chưa hỗ trợ`; component test không có toast | PASS |

## Cổng chất lượng cuối vòng

| Cổng | Kỳ vọng | Kết quả hiện tại |
|---|---|---|
| Backend targeted | P0.1/P0.2/P0.3/P0.5 integration pass | PASS: GIS 8/8; PostGIS 10/10; Phase10 11/11; standalone benchmark 6/6 |
| Frontend targeted | P0.1/P0.2/P0.3/P0.4/P0.5/P1 component/contract pass | PASS: WebGIS 8/8; AssetList 11/11; MainLayout 3/3; UserManagement 12/12; full 81/81 (13 suites) |
| Backend benchmark | Không regression ngưỡng tự động | PASS: 6/6; keyword p95 `86 ms`; GIS benchmark dưới 60 giây |
| Full backend suite | Không regression | PASS: 122 tests, 122 pass, `BUILD SUCCESS` |
| Full frontend suite | 100% | PASS: 81/81 (13 suites) |
| Playwright | Luồng chính pass | PASS: 8/8 trên Chromium |
| Frontend production build | TypeScript và Vite build | PASS; còn cảnh báo kích thước chunk vendor Ant Design |

P0.1 đến P0.5 và P1.001/P1.002/P1.005 được chấp nhận với bằng chứng live, contract/integration/component/E2E/build. KCHT-P1-004 đã được nghiệm thu. Residual risk: cảnh báo chunk vendor Ant Design lớn và các cảnh báo React Router/act trong test harness không ảnh hưởng chức năng đã kiểm chứng.

## Acceptance P0.4/P0.5

| Tiêu chí | Bằng chứng | Kết quả |
|---|---|---|
| Production không lộ raw exception | `CommonComponents.test.tsx` kiểm message/path/stack không xuất hiện | PASS |
| Error ID không chứa dữ liệu nhạy cảm | ID sinh cục bộ dạng `ui-<timestamp>-<sequence>` | PASS |
| GIS request hủy khi thay đổi lớp/bbox/filter | `AbortController` trong `WebGisPage`, signal truyền vào Axios; component test filter-change abort | PASS |
| Không request/toast trùng | request key + throttle 3 giây theo dataset/lỗi; component test | PASS |
| Empty khác exception | empty FeatureCollection hiện thông báo thông tin, không gọi error toast | PASS |
| Geometry/SRID/limit/RBAC | 19 backend GIS integration assertions (10 tests gồm lớp đường `tbl_km_post`, `mst_national_road`, `tbl_bridge`, `tbl_road_sign`, `road_sphere_mirror`); Point coordinates trong bbox, 403/400 | PASS |

## Acceptance P0.6 (Tiếng Việt & Profile Cache)

| Tiêu chí | Bằng chứng | Kết quả |
|---|---|---|
| Database UTF-8 không còn dấu `?` | Flyway migration V10; assertion `SELECT count(*) FROM app_user WHERE full_name LIKE '%?%'` = 0; `app_role` = 0 | PASS |
| Loại bỏ cache localStorage hỏng | `AuthContext` hàm `getInitialCachedUser` và `isProfileValid` tự động phát hiện chuỗi chứa `?` và xóa cache; đồng bộ `kcht_profile_cache_version=v2_utf8` | PASS |
| Không sửa bằng regex/replace `?` ở frontend | Frontend từ chối dữ liệu hỏng và làm mới từ `/api/auth/me`; không dùng replace giả | PASS |
| Backend API & Header UTF-8 | Cấu hình `server.servlet.encoding.charset=UTF-8`, `force=true`; bean `CharacterEncodingFilter`; `VietnameseEncodingIntegrationTest` kiểm tra API trả UTF-8 chuẩn | PASS |
| Kiểm thử từ khóa bắt buộc | `VietnameseEncoding.test.tsx` (5 tests) và `VietnameseEncodingIntegrationTest` (3 tests) kiểm thử đầy đủ: `Quản trị viên`, `Hệ thống`, `Đường quốc lộ`, `Cầu đường bộ`, `Tỉnh/thành phố` | PASS |
| Live UI | Lời chào hiển thị: `Xin chào, Quản trị viên Hệ thống.`; vai trò `Quản trị viên Toàn quyền`; không còn mojibake `Qu???n tr??? vi??n` | PASS |

## Acceptance P2 (Bảo mật cấu hình & Chất lượng TypeScript)

| Tiêu chí | Bằng chứng | Kết quả |
|---|---|---|
| Xóa bỏ default secrets trên Production | `application-prod.yml` bắt buộc `${JWT_SECRET}`, `${POSTGRES_PASSWORD}`, `${MINIO_ROOT_USER}`, `${MINIO_ROOT_PASSWORD}`, không có giá trị fallback mặc định | PASS |
| Loại trừ `.env`, khóa riêng và dump khỏi source/image | `.gitignore` và `.dockerignore` loại trừ `.env`, `.env.*`, `*.key`, `*.pem`, `*.pfx`, `*.dump`, `deploy/backups/`, cho phép template `.env.example` | PASS |
| Cookie refresh cấu hình qua môi trường | `AuthApiController` inject `@Value` cookie name, secure, same-site, domain, path, maxAge; `application-prod.yml` cấu hình HTTPS `secure=true` | PASS |
| Kiểm thử Cookie Security | `AuthCookieSecurityTest` (2 tests) xác nhận HTTP dev mode (`secure=false`, `SameSite=Lax`) và Production mode (`secure=true`, `SameSite=Strict`, `Domain=drvn.gov.vn`) | PASS: 2/2 |
| Loại bỏ `as any` tại các đường dữ liệu | Quy tắc ESLint `@typescript-eslint/no-explicit-any: error`, `strict: true` trong tsconfig; quét 0 kết quả `as any` | PASS |
| Lint & Production Build | `npm run lint` 0 warnings; `npm run build` thành công exit 0 | PASS |
