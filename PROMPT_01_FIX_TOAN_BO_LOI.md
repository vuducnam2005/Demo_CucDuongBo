# PROMPT 01 - Sửa toàn bộ lỗi đã rà soát trong hệ thống KCHT

Bạn là kỹ sư full-stack senior chịu trách nhiệm trực tiếp sửa mã nguồn, chạy ứng dụng, tái hiện lỗi, kiểm thử và hoàn thiện hệ thống quản lý kết cấu hạ tầng đường bộ tại:

`C:\Demo_CucDuongBo`

Stack hiện tại gồm Spring Boot 3/Java 21, React 18/TypeScript/Vite, Ant Design, TanStack Query, PostgreSQL/PostGIS, Flyway, Vitest và Playwright.

## Kết quả bắt buộc

Hãy sửa thật trong source code, chạy thử và tự debug đến khi các luồng chính hoạt động ổn định. Không chỉ phân tích, viết kế hoạch hoặc đề xuất đoạn mã. Không báo hoàn thành nếu chưa có bằng chứng chạy thực tế và test phù hợp.

Ưu tiên sửa nguyên nhân gốc ở API contract, DTO, mapper, query, encoding, trạng thái bất đồng bộ và component. Không che lỗi bằng mock data, `as any`, `try/catch` rỗng, giá trị mặc định sai sự thật hoặc thông báo thành công giả.

## Quy tắc bảo vệ hệ thống và dữ liệu

1. Đọc trước `AI_RULES.md`, `AI_WORKFLOW.md`, `ARCHITECTURE.md`, `API_CONVENTIONS.md`, `DATA_CONTRACT.md`, `DEFINITION_OF_DONE.md`, `TEST_MATRIX.md`, Flyway migrations và tài liệu trong `docs`.
2. Thư mục hiện tại có thể không phải Git repository. Không được giả định Git tồn tại, không tự chạy `git init`, không xóa/ghi đè thay đổi hiện có. Hãy kiểm kê file trước khi sửa và ghi lại file đã thay đổi trong báo cáo.
3. Không xóa database, không chạy `clean`, không reset schema và không chạy lại toàn bộ import dữ liệu. Nếu cần kiểm thử import, chỉ dùng fixture nhỏ, tách biệt và có thể hoàn tác.
4. Không hard-code hoặc in ra log mật khẩu, JWT, cookie, khóa riêng, chuỗi kết nối hay secret. Không sao chép giá trị thật từ `.env` hoặc `application.yml` vào tài liệu.
5. Không bypass CAPTCHA, đăng nhập, phân quyền hoặc cơ chế bảo mật. Chỉ kiểm thử với quyền mà tài khoản hiện tại được cấp.
6. Không thay đổi schema/API lớn chỉ để né lỗi frontend. Nếu buộc phải thay contract, cập nhật đồng bộ backend, OpenAPI, frontend, test và tài liệu tương thích.
7. Mọi thao tác tạo/sửa/xóa phải chỉ báo thành công sau khi API thật trả thành công. Khi thất bại phải giữ dữ liệu người dùng nhập và hiển thị lỗi có ích.

## Giai đoạn 0 - Tái hiện và lập hồ sơ lỗi

Trước khi sửa, hãy chạy backend/frontend theo hướng dẫn dự án và kiểm tra tối thiểu các route thực tế sau:

- `/dashboard`
- `/assets`
- `/map`
- `/reports`
- `/documents`
- `/catalogs`
- `/admin/users`
- `/admin/audit-logs`

Với mỗi route, ghi lại request, response shape, status code, console error, loading/empty/error state, filter, sort, phân trang, quyền truy cập, tiếng Việt và độ khớp giữa UI với API/database.

Tạo hoặc cập nhật:

- `docs/qa/UI_FUNCTIONAL_AUDIT.md`
- `docs/qa/BUG_MATRIX.md`
- `docs/qa/ROUTE_ACCEPTANCE_MATRIX.md`

Mỗi lỗi phải có mã, mức P0/P1/P2, cách tái hiện, nguyên nhân gốc, file liên quan, phương án sửa, test hồi quy và trạng thái nghiệm thu. Các nguyên nhân đã xác nhận bên dưới phải được xem là điểm bắt đầu; hãy kiểm chứng lại bằng source và response thật trước khi sửa.

## P0 - Lỗi làm hỏng chức năng, sai dữ liệu hoặc lộ thông tin

### P0.1 - Trang quản lý người dùng bị crash do sai contract phân trang

Hiện trạng đã xác nhận:

- Backend `GET /api/auth/users` trả `PagedResponse<UserSummaryDto>` gồm `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- `frontend/src/services/api.ts` đang khai báo `fetchUsers(): Promise<UserSummary[]>`.
- `frontend/src/pages/UserManagementPage.tsx` coi toàn bộ response là mảng rồi gọi `.filter()`, khiến Ant Design/Table có thể báo `TypeError: rawData.some is not a function`.

Yêu cầu sửa:

- Sửa type của client thành `PagedResponse<UserSummary>` và truyền `response.content` vào bảng.
- Chuẩn hóa response phân trang bằng utility dùng chung có type chặt chẽ; không dùng `as any` để né lỗi.
- Thực hiện phân trang phía server, đồng bộ `page`, `size`, `totalElements`, loading khi chuyển trang và query key của TanStack Query.
- Tìm kiếm/lọc phải có hành vi rõ ràng: nếu backend chưa hỗ trợ thì bổ sung tham số có contract và test; không lọc một trang rồi giả vờ là toàn bộ dữ liệu.
- Kiểm tra response rỗng, 401, 403, 5xx và retry.
- Thêm contract/integration test backend và component test frontend để lỗi object-vs-array không tái diễn.

### P0.2 - Chức năng thêm người dùng và phân quyền đang thành công giả

Hiện trạng đã xác nhận:

- Backend đã có `POST /api/auth/users`.
- Modal tạo người dùng hiện chỉ validate form rồi gọi `message.success`, không gửi API.
- Nút `Phân quyền` hiện chỉ gọi `message.info`.

Yêu cầu sửa:

- Nối modal tạo người dùng với API thật, dùng DTO đúng field của backend, có trường mật khẩu theo policy nhưng không lưu/log mật khẩu.
- Hiển thị validation từ backend, chống submit lặp, đóng modal và refresh danh sách chỉ sau response thành công.
- Kiểm kê API cập nhật role/quyền. Nếu endpoint đã có thì nối thật và kiểm thử RBAC; nếu chưa có thì triển khai theo kiến trúc hiện tại hoặc vô hiệu hóa nút với nhãn rõ ràng, tuyệt đối không báo thành công giả.
- Kiểm kê toàn dự án để tìm mọi nút/luồng đang chỉ hiển thị success/info mà không có request thành công; sửa hoặc vô hiệu hóa trung thực.

### P0.3 - Báo cáo sai tên trường nên hiển thị trung bình và tỷ lệ bằng 0

Hiện trạng đã xác nhận:

- Backend `RoadLengthReportDto.ReportSummary` dùng `averageLengthKm`.
- Frontend `reportApi.ts` đang dùng `avgLengthKm`.
- Backend các phần distribution dùng `percent`.
- Frontend đang dùng `percentage`.

Yêu cầu sửa:

- Dùng backend DTO/OpenAPI làm nguồn sự thật và đồng bộ type frontend với `averageLengthKm` và `percent`.
- Chỉ dùng response adapter nếu thật sự cần tương thích dữ liệu cũ; adapter phải có test và không âm thầm đổi `undefined` thành `0`.
- Rà soát toàn bộ DTO báo cáo bảo trì, biển báo/điểm đen, export CSV và component hiển thị để phát hiện các lệch field khác.
- Đối chiếu tổng chiều dài, số tuyến, chiều dài trung bình, phân bố và tỷ lệ với SQL chạy trực tiếp trên cùng bộ lọc.
- Kiểm thử các trường hợp null, tổng bằng 0, số thập phân, số lớn, đơn vị mét/km và format `vi-VN`.

### P0.4 - Error Boundary làm lộ chi tiết nội bộ

Hiện trạng đã xác nhận: `frontend/src/components/common/ErrorBoundary.tsx` đang render tên lỗi, message và component stack ra giao diện, có thể lộ `node_modules`, đường dẫn source và chi tiết kỹ thuật.

Yêu cầu sửa:

- Ở production chỉ hiển thị thông báo thân thiện, nút tải lại/quay về trang chủ và mã lỗi/correlation ID không chứa dữ liệu nhạy cảm.
- Chi tiết kỹ thuật chỉ được phép hiện trong development; log production phải đi qua cơ chế logging/monitoring phù hợp và được lọc dữ liệu nhạy cảm.
- Không đưa raw exception từ backend ra UI.
- Thêm test chứng minh build production không render stack trace, đường dẫn file hoặc message nội bộ.

### P0.5 - WebGIS không tải lớp dữ liệu và lặp toast lỗi

Hiện trạng quan sát được: nền bản đồ tải được nhưng lớp chọn có 0 đối tượng, request lớp dữ liệu thất bại và toast `Không thể tải dữ liệu bản đồ cho lớp đã chọn` bị lặp.

Hãy kiểm tra cho từng request GIS:

- endpoint, method, dataset key, layer registry và quyền;
- bbox, thứ tự tọa độ, zoom, limit và kích thước response;
- SRID PostGIS, yêu cầu chuẩn hóa về EPSG:4326 và GeoJSON;
- geometry null/invalid, kiểu geometry và mapping ID;
- status code, response body, timeout và lỗi hủy request.

Yêu cầu sửa:

- Query phía server theo bbox; không tải toàn bộ dataset lớn vào browser.
- Hủy request cũ bằng `AbortController` khi map/layer/filter thay đổi và ngăn request trùng.
- Mỗi layer có trạng thái loading/empty/error/retry riêng. Lỗi một layer không làm hỏng layer khác.
- Toast phải được deduplicate/throttle theo layer và lỗi; một sự cố không tạo nhiều toast giống nhau.
- Empty state hợp lệ không được coi là exception.
- Kiểm thử ít nhất một lớp đường, `tbl_bridge`, `tbl_road_sign` và một dataset geometry lớn.
- Thêm integration test bbox/SRID/GeoJSON/quyền và component test chống request/toast lặp.

### P0.6 - Tiếng Việt bị biến thành dấu hỏi

Hiện trạng quan sát được có chuỗi dạng `Qu???n tr??? vi??n` và lời chào bị hỏng.

Điều tra toàn chuỗi:

- giá trị thật trong PostgreSQL;
- encoding của migration, seed, JSON và source;
- JDBC/database encoding;
- header/body JSON của API;
- cách Axios/browser giải mã;
- profile đã cache tại `localStorage` với key như `kcht_user_profile`;
- font fallback và quá trình render.

Yêu cầu sửa:

- Chuẩn hóa UTF-8 từ database đến UI.
- Nếu localStorage chứa dữ liệu hỏng cũ, triển khai version/migration hoặc làm mới profile an toàn sau đăng nhập; không âm thầm giữ cache lỗi.
- Không sửa bằng cách thay dấu `?` ở frontend.
- Nếu dữ liệu trong database đã mất ký tự, tạo script/migration dữ liệu có kiểm soát, có backup và chỉ chạy khi xác định được nguồn đúng.
- Thêm test với `Quản trị viên`, `Hệ thống`, `Đường quốc lộ`, `Cầu đường bộ`, `Tỉnh/thành phố`.

## P1 - Lỗi hiển thị, khả dụng và tính nhất quán

### P1.1 - Bảng tài sản bị tràn và cột thao tác bị cắt

- Thiết lập scroll ngang có chủ đích và cột thao tác sticky nếu phù hợp.
- Không để nút chi tiết/sửa/xóa bị che.
- Metadata động phải có allowlist, thứ tự cột ổn định, độ rộng hợp lý và label tiếng Việt.
- Phân biệt null thật với sai mapping field; không dùng `—` để che lỗi contract.
- Phân trang server-side không lặp hoặc mất bản ghi.
- Tạo cách hiển thị phù hợp trên mobile, có thể dùng card/detail drawer thay vì ép bảng quá rộng.
- Chuyển trạng thái kỹ thuật như `RAW_STORED` thành nhãn tiếng Việt có tooltip; giữ raw value trong dữ liệu kỹ thuật khi cần.

### P1.2 - Loading, empty, error và retry không nhất quán

Rà soát mọi trang và dùng pattern chung cho:

- skeleton/loading không gây nhảy bố cục;
- empty state đúng nguyên nhân;
- lỗi 401/403/404/409/422/500 có thông điệp khác nhau;
- retry có kiểm soát, không tạo request loop;
- thao tác mutation có trạng thái pending và chống double click;
- query cache được invalidate đúng sau mutation.

### P1.3 - Route, breadcrumb và quyền không đồng bộ

- Menu chỉ hiện mục người dùng có quyền truy cập.
- Truy cập URL trực tiếp phải được `ProtectedRoute` kiểm soát đúng role/permission.
- Breadcrumb, selected menu và expanded menu phải phản ánh đúng route hiện tại, kể cả refresh trang.
- 403 phải có trang/thông báo riêng, không biến thành 404 hoặc màn hình trắng.

## P2 - Chất lượng, bảo mật cấu hình và khả năng vận hành

### P2.1 - Secret mặc định trong cấu hình

Rà soát `application.yml`, Docker Compose và file môi trường:

- Xóa mọi secret mặc định có thể dùng được; production phải yêu cầu biến môi trường.
- `.env`, private key, token và file credential phải bị loại khỏi source/package; giữ `.env.example` chỉ với placeholder vô hại.
- Tách cấu hình development/test/production.
- Cookie refresh phải lấy thuộc tính `Secure`, `SameSite`, domain/path và TTL từ cấu hình môi trường; production HTTPS phải dùng `Secure=true`.
- Nếu credential từng bị đưa vào source/log, ghi rõ cần rotate nhưng không in lại credential.

### P2.2 - Rà soát kiểu dữ liệu và chất lượng TypeScript

- Loại bỏ `as any` tại các đường dữ liệu chính.
- Dùng type guard/schema validation ở ranh giới API nếu cần.
- Không giả định field luôn tồn tại khi backend cho phép null.
- `npm run lint` và `npm run build` phải đạt mà không hạ mức rule để né lỗi.

## Thứ tự thực hiện bắt buộc

1. Tái hiện lỗi và lập ba tài liệu QA.
2. Sửa contract người dùng, mutation tạo người dùng và các success giả.
3. Sửa contract báo cáo và kiểm chứng SQL.
4. Sửa ErrorBoundary và lỗi encoding.
5. Sửa WebGIS từ endpoint đến UI.
6. Sửa bảng tài sản, pagination và các trạng thái UI dùng chung.
7. Rà soát route/RBAC và cấu hình bảo mật.
8. Chạy toàn bộ test phù hợp, chạy ứng dụng và kiểm tra thủ công các route chính.

Không chuyển sang công việc đồng bộ giao diện với web mẫu khi còn lỗi P0.

## Kiểm thử và lệnh xác minh

Chạy các lệnh tương ứng với môi trường dự án, tối thiểu:

```powershell
cd C:\Demo_CucDuongBo
.\mvnw.bat test

cd C:\Demo_CucDuongBo\frontend
npm run lint
npm run test
npm run build
npm run test:e2e
```

Nếu một lệnh không chạy được do phụ thuộc môi trường, phải ghi chính xác lệnh, lỗi, phần nào đã xác minh bằng cách khác và việc còn lại. Không được tự tuyên bố pass.

Test bắt buộc gồm:

- backend integration/contract test cho users, reports và GIS;
- frontend unit/component test cho adapter phân trang, User Management, reports, WebGIS và ErrorBoundary;
- test UTF-8;
- test pagination không lặp/mất bản ghi;
- test RBAC cho viewer/editor/manager/admin;
- Playwright E2E cho đăng nhập, dashboard, assets, map, reports, documents, catalogs và admin/users theo quyền phù hợp.

## Điều kiện nghiệm thu

Chỉ được báo hoàn thành khi:

- `/admin/users` không còn crash, hiển thị đúng `content`, tổng bản ghi và phân trang server-side;
- tạo người dùng gửi API thật và không có thông báo thành công giả;
- báo cáo dùng đúng field backend, các giá trị trung bình/tỷ lệ khớp SQL;
- production UI không lộ stack trace hoặc đường dẫn nội bộ;
- WebGIS tải được lớp hợp lệ, không spam request/toast và xử lý layer độc lập;
- tiếng Việt hiển thị đúng từ database/API đến localStorage/UI;
- bảng tài sản không mất cột thao tác ở desktop và có phương án dùng được trên mobile;
- các route chính có loading/empty/error/retry đúng;
- không còn secret dùng được làm giá trị mặc định;
- test backend, lint, unit test, build và E2E liên quan đạt.

## Báo cáo cuối cùng

Trả về báo cáo ngắn gọn nhưng có bằng chứng:

1. Lỗi đã tái hiện, nguyên nhân gốc và cách sửa.
2. Danh sách file đã thay đổi.
3. API/schema/migration có thay đổi hay không và lý do.
4. Các lệnh test đã chạy cùng kết quả pass/fail.
5. Route đã kiểm tra thực tế và viewport đã dùng.
6. Lỗi còn lại, mức độ, nguyên nhân chưa xử lý và bước tiếp theo.

Không chuyển sang PROMPT 02 nếu một điều kiện nghiệm thu P0 chưa đạt.
