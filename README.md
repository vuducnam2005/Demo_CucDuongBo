# DEMO ĐỘC LẬP: QUẢN LÝ KẾT CẤU HẠ TẦNG ĐƯỜNG BỘ
**Mục đích:** thử nghiệm nghiệp vụ trên dữ liệu người dùng cấp; không phải cổng chính thức của Cục Đường bộ Việt Nam.
**Kiến trúc:** Modular Monolith (Spring Boot 3 + Java 21) & Frontend SPA (React + TypeScript + Vite + Ant Design)  
**Cơ sở Dữ liệu & Lưu trữ:** PostgreSQL 16 + PostGIS 3.4+, tệp cục bộ; MinIO là profile tùy chọn.

---

## 1. KHỞI CHẠY HẠ TẦNG CỤC BỘ

### Bước 1: Chuẩn bị biến môi trường
Sao chép `.env.example` thành `.env` và thay các placeholder bằng bí mật được sinh riêng; không dùng mật khẩu trang tham chiếu, không commit `.env`:
```bash
cp .env.example .env
```

### Bước 2: Khởi động PostgreSQL và Flyway
Compose mặc định chạy PostgreSQL/PostGIS và Flyway (20 migration). Nếu Flyway từng kết thúc trước khi có migration mới, dùng `docker compose run --rm flyway` để chạy lại. Hồ sơ demo lưu tệp trong `data/storage/` cục bộ (Git bỏ qua), không cần bật MinIO.
```bash
docker compose up -d
```

Kiểm tra trạng thái các container:
```bash
docker compose ps
```
*Kỳ vọng:* `kcht_postgres` ở trạng thái `Up (healthy)` và `kcht_flyway` kết thúc mã 0.

### Nhập dữ liệu VroadAI đã chuẩn hóa
Chạy trên thư mục JSONL đã đối soát từ Excel (không commit thư mục dữ liệu này):
```powershell
python -X utf8 scripts/import_vroadai_normalized.py "<đường-dẫn-thư-mục-jsonl>"
```
Chỉ nhập 3 tệp `assets.jsonl`, `defects.jsonl`, `iri.jsonl`: kỳ vọng lần lượt 2.614, 3.404 và 221 mã nguồn; 2 báo cáo lỗi theo tuyến chỉ dùng đối chiếu. Nhập lần nữa không nhân đôi. Bản ghi ở trạng thái `RAW_STORED`, chưa duyệt và chưa gán vùng.

Để trình diễn riêng thao tác của hai tài khoản đơn vị mà không gán nhầm bản ghi nguồn cho một địa bàn, chạy:
```powershell
python -X utf8 scripts/seed_local_region_demo.py
```
Tác vụ tạo **2 bản ghi MÔ PHỎNG** cho `kqldb_1`, chạy lặp không nhân đôi. Không áp dụng nguồn này để đối soát thống kê thật.

File ZIP người dùng cung cấp chỉ chứa một phần danh mục đường; kiểm tra manifest và nhập **riêng** 169 quốc lộ và 51 cao tốc, không nhập 5.988 dòng danh mục khác:
```powershell
python -X utf8 scripts/import_road_master_zip.py "C:\Users\linzi\Downloads\kcht_2026-10-05.zip"
```
Manifest ghi `complete_website_crawl=false`: số lượng này chưa chứng minh là toàn bộ hệ thống nguồn. Nhập lại không tạo bản ghi trùng.

### Ảnh hư hỏng/tài sản trong Excel
Hai file thư viện **không nhúng ảnh**: mỗi bản ghi có hyperlink ở cột `Hình ảnh`, dẫn đến trang chia sẻ `platform.vroad.vn`. Sau khi import dữ liệu nguồn và chạy Flyway v16, nhập hyperlink theo mã nguồn (không tải ảnh hàng loạt):
```powershell
python -X utf8 scripts/import_vroadai_image_links.py "C:\Users\linzi\Downloads"
```
Đã đối soát 2.614 link tài sản và 3.404 link hư hỏng; chạy lặp không nhân đôi. Trong bản đồ, click **một** điểm hoặc tìm mã DEF để xem thông tin riêng điểm đó, ảnh mở trực tiếp và có nút phóng to (phụ thuộc quyền truy cập trang ảnh của trình duyệt). Dữ liệu IRI trong Excel chỉ có lý trình, **không có tọa độ hay hình tuyến** nên không được tự vẽ đoạn đường IRI lên bản đồ.

---

## 2. KHỞI CHẠY BACKEND SERVICE (SPRING BOOT 3 + JAVA 21)

Backend cung cấp hệ thống REST API, WebGIS GeoJSON endpoints, và Actuator Health check.

### Chạy ứng dụng Web API:
- **Trên Windows PowerShell:**
  ```powershell
  Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') } }
  .\mvnw.bat spring-boot:run
  ```
- **Trên Linux / macOS:**
  ```bash
  set -a; . ./.env; set +a; mvn spring-boot:run
  ```

Trong terminal khác, chạy `cd frontend; npm ci; npm run dev -- --host 127.0.0.1`. Mở `http://127.0.0.1:3000` (API `http://127.0.0.1:8089`). Bốn mật khẩu đăng nhập demo **được đặt chung theo yêu cầu, chỉ cho máy cục bộ, và lưu trong `.env`** dưới các khóa `DEMO_ADMIN_PASSWORD`, `DEMO_EDITOR_PASSWORD`, `DEMO_MANAGER_PASSWORD`, `DEMO_VIEWER_PASSWORD`; mở file trên máy để xem. Để tạo lại mật khẩu đồng thời cập nhật hash trong DB, chạy `powershell -NoProfile -File scripts/rotate_local_demo_passwords.ps1` (ngẫu nhiên từng tài khoản) hoặc thêm `-Shared` để nhập mật khẩu chung qua prompt ẩn. `.env` chứa mật khẩu rõ theo yêu cầu thử nghiệm, bị Git bỏ qua: không gửi, chụp ảnh hoặc commit file. `JWT_SECRET`/`POSTGRES_PASSWORD` không phải mật khẩu đăng nhập web. `admin` xem dashboard Cục, bản đồ hư hỏng và hồ sơ; `editor_demo` mở bản ghi mô phỏng gửi kiểm tra; `manager_demo` xác nhận/trả lại bản ghi mô phỏng có lý do. **Chưa có dữ liệu nguồn nào được xác nhận thuộc vùng này: 0 tài sản Excel và 2 bản ghi MÔ PHỎNG.**

**Dashboard quản trị:** admin mở `/admin/operations` để xem tổng bản ghi, tập dữ liệu, tài khoản, hồ sơ, lô VroadAI trong vùng chờ; biểu đồ tập dữ liệu, nhật ký 7 ngày, hồ sơ theo đơn vị, vai trò và phân loại xe (mô phỏng/nhập tách nhãn). API `GET /api/admin/operations/summary` lấy số liệu từ PostgreSQL, chỉ admin truy cập. `/traffic` hiển thị trạm mô phỏng riêng và phép đếm/lát cắt mật độ nhập; admin vào `/admin/traffic-import` để dán hoặc chọn tệp JSON, tải mẫu rồi nhập qua `POST /api/vroad/traffic/imports`. Có chống trùng, phân quyền, audit và tỷ lệ xe/km khi có chiều dài đoạn đo. **Các XLSX hiện cung cấp không có số liệu đếm xe hay mật độ thực; chưa kết nối VroadAI chính thức.** Xem `docs/integration/traffic-observations.md` để biết mẫu và cách kiểm tra API.

Tài khoản quản lý (Cục hoặc đơn vị khi đã có phân vùng nguồn được xác minh) có thể mở `/map`, chọn hư hỏng → **Đã xử lý xong**, nhập nội dung và tùy chọn ảnh JPEG/PNG/WebP tối đa 5 MiB. Trạng thái, người xác nhận, thời điểm và ảnh lưu vào `/cases` và audit; bản ghi RAW Excel không bị sửa. Xác nhận lại cùng bản ghi nhận lỗi 409. Tài khoản vùng hiện chưa có hư hỏng thật để xác nhận, **không gán nhầm dữ liệu QL1 cho vùng I bằng suy đoán địa lý**.

Trên bản đồ, điểm đỏ là hư hỏng, điểm xanh là tài sản; bộ lọc **Tất cả / Hư hỏng / Tài sản** giúp chọn đúng lớp khi hai loại cùng tọa độ. Mỗi lớp giới hạn 500 điểm trong khung nhìn và báo khi cần phóng to. Chỉ nhấn **điểm đánh dấu** hoặc tìm mã trong khung mới mở thông tin và ảnh tương ứng; nhấn nền trống sẽ xóa lựa chọn, không hiển thị đoạn gần nhất chưa được chọn. Nhấn ảnh để phóng to, hoặc mở trang ảnh nguồn nếu bên cung cấp không cho nhúng.

Trong danh sách tài sản VroadAI, chọn **Mở GIS** để định vị chính bản ghi đã chọn: bản đồ hiển thị ảnh tài sản, tuyến, lý trình và tình trạng; ảnh mở lớn được, bấm nền trống sẽ bỏ chọn. Hư hỏng gần tài sản không tự tải hay hiển thị; chọn **Xem hư hỏng gần tài sản** mới tra cứu theo đúng tuyến, lý trình và phía tài sản, trong phạm vi 20 m, tối đa 30 điểm. API `GET /api/vroad/assets/{id}/map` mặc định chỉ trả bản ghi tài sản; chỉ khi truyền `includeRelated=true` mới trả danh sách hư hỏng liên quan (vẫn kiểm tra quyền theo đơn vị). Nếu mã `QL` trong tên tuyến khớp duy nhất với danh mục quốc lộ đã nhập, tài khoản quản trị thấy thêm thông tin **toàn tuyến** (không ngầm coi đây là thuộc tính riêng đoạn khảo sát); tài khoản vùng không nhận thông tin danh mục trung ương ngoài phạm vi.

Từ chi tiết tài sản hoặc bản đồ, chọn **Tra cứu hồ sơ tài sản** để mở kho hồ sơ cục bộ với mã tài sản đã được điền vào ô tìm kiếm và ô mã liên kết khi tải tệp. Kho hồ sơ chỉ tìm/ghi dữ liệu PostgreSQL và tệp cục bộ theo quyền tài khoản; đường dẫn này không tạo hoặc tải tài liệu từ VroadAI hay trang của Cục.

**Hồ sơ theo đơn vị:** `/documents` sử dụng `/api/local-documents` (không dùng danh sách raw tài liệu cũ). Admin xem toàn bộ; manager vùng chỉ xem/tải/xóa mềm/đổi tên hồ sơ có `branch_id` của mình; editor vùng được tải lên, viewer có chi nhánh chỉ đọc. Tạo thư mục lồng, tìm kiếm, lọc định dạng, phân trang, lịch sử phiên bản và tải từng bản cũ; tệp lưu cục bộ, metadata và audit trong PostgreSQL. Chọn thư mục dùng ID thực, không dùng mã thư mục. Bản ghi chưa có nhị phân **không có tệp tải giả**. Tài liệu tạo để thử nghiệm không phải tài liệu chính thức. Chi tiết API, phân quyền và hạn chế tại `docs/document-versioning.md`.

**Lưu lượng:** `/traffic` lấy trạm và thống kê phân loại từ PostgreSQL qua `/api/vroad/traffic/stations` và `/summary`. Quan sát gốc là 15 phút, biểu đồ tổng hợp **theo giờ**; mặc định xem 31 ngày gần nhất có dữ liệu, có thể lọc khoảng thời gian khác tối đa 31 ngày. Trạm mẫu và 32 quan sát là **MÔ PHỎNG**, không có tọa độ. Chưa đủ chiều dài đoạn và vận tốc khảo sát để tính mật độ xe/km thật.

**VroadAI (đề xuất, chưa kết nối ngoài):** Admin mở `/admin/vroad-inbound`, hoặc dùng `POST /api/vroad/inbound/batches` với JWT cục bộ để lưu lô JSON trong staging; xem lỗi từng dòng qua `GET /api/vroad/inbound/batches/{id}`. Gửi lặp cùng mã + cùng nội dung không tăng bản ghi; khác nội dung trả 409. Mẫu Excel **đề xuất chờ xác nhận** nằm tại `data/templates/vroad_inbound_proposal.xlsx`, có thể chuyển sang JSON (không gửi tự động):
```powershell
python -X utf8 scripts/convert_vroad_proposal_excel.py data/templates/vroad_inbound_proposal.xlsx --request-key demo-local-001 --output scratch/vroad-batch.json
```
Xem `docs/integration/vroad-inbound-proposal.md`. **Không** coi schema/endpoint demo là hợp đồng VroadAI; chưa nhập lô staging vào bảng tài sản/hư hỏng chính thức.

Xem `docs/source-guide-review.md`, `docs/assumptions.md`, `docs/status.md` trước khi demo. Không chạy `docker compose down -v`: thao tác đó xóa dữ liệu PostgreSQL.

### Các Endpoint Nền tảng:
- **Health Check:** `http://localhost:8089/actuator/health` (Phản hồi `{"status": "UP"}`)
- **Swagger UI (OpenAPI 3):** `http://localhost:8089/swagger-ui/index.html`
- **OpenAPI JSON Spec:** `http://localhost:8089/v3/api-docs`
- Swagger đánh dấu các tuyến `/api/**` cần JWT. Đăng nhập qua `POST /api/auth/login`, lấy `accessToken` và dùng nút **Authorize** để thử API trong đúng quyền tài khoản. Ba tuyến đăng nhập/làm mới/đăng xuất không yêu cầu access token; API quản trị chỉ dành cho vai trò được cấp. Đây là API của bản demo, **không phải API mở của VroadAI hay Cục**.
- **Xác thực & Phiên (Auth):** `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`
- **Quản trị Người dùng (RBAC):** `GET /api/auth/users`, `POST /api/auth/users`
- **Nhật ký Kiểm toán (Audit):** `GET /api/audit-logs`
- **Danh mục tập dữ liệu hiện có:** `http://localhost:8089/api/datasets`
- **MinIO Console:** `http://localhost:9011` (User: `kcht_minio_user`)

### Tài khoản mẫu đăng nhập (mật khẩu xem trong `.env` cục bộ):
- **Quản trị viên (`ROLE_ADMIN`):** `admin` / `<cấp qua kênh bảo mật>`
- **Lãnh đạo (`ROLE_MANAGER`):** `manager_demo` / `<cấp qua kênh bảo mật>`
- **Chuyên viên Kỹ thuật (`ROLE_EDITOR`):** `editor_demo` / `<cấp qua kênh bảo mật>`
- **Cán bộ Tra cứu (`ROLE_VIEWER`):** `viewer_demo` / `<cấp qua kênh bảo mật>`

---

## 3. KHỞI CHẠY IMPORT WORKER ĐỘC LẬP (INDEPENDENT WORKER)

Tác vụ nạp dữ liệu lớn được thiết kế tách biệt hoàn toàn với Web Request, sử dụng cơ chế Streaming Jackson $O(1)$ RAM, batch 500 bản ghi, có checkpoint và resume:

### Các lệnh nạp dữ liệu độc lập:
1. **Kiểm tra mô phỏng (Dry-run không ghi CSDL):**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--dry-run --file=assets/duonggom.json"
   ```
2. **Đồng bộ từ điển trường & metadata từ manifest.json:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--sync-registry"
   ```
3. **Nạp tệp cụ thể:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--file=assets/tbl_bridge.json"
   ```
4. **Nạp TOÀN BỘ 658 tệp dữ liệu:**
   ```powershell
   .\mvnw.bat spring-boot:run -Dspring-boot.run.arguments="--all"
   ```

---

## 4. KHỞI CHẠY FRONTEND (REACT + TYPESCRIPT + VITE + ANT DESIGN)

Giao diện người dùng được tổ chức trong thư mục `frontend/`.

```bash
cd frontend
npm install
npm run dev
```
Truy cập giao diện Web: `http://localhost:3000`  
Vite dev server đã được cấu hình proxy tự động chuyển tiếp các request `/api` và `/actuator` sang backend cổng `8089`.

---

## 5. BỘ LỆNH KIỂM THỬ, LINT VÀ FORMAT (TESTING, LINT & FORMAT)

### Kiểm thử Backend:
Chạy nhóm unit test đã kiểm chứng (không dùng DB demo). Bộ test tích hợp đầy đủ cần một **database test riêng**, không trỏ vào PostgreSQL đang chứa dữ liệu demo:
```powershell
.\mvnw.bat -q '-Dtest=BranchScopeFilterTest,RegionApiControllerTest,RegionReviewControllerTest,DatasetPermissionServiceTest' test
```
Lượt chạy `mvnw.bat test` ngày 07/10/2026 chưa đạt do test DB cấu hình mật khẩu không khớp (SQLSTATE 28P01); xem `docs/status.md`.

Đã kiểm thử luồng VroadAI trong môi trường demo: Java unit tests, Python hyperlink parser, toàn bộ frontend Vitest và Playwright (Chrome có sẵn ở máy). Chạy lại:
```powershell
.\mvnw.bat -q '-Dtest=VroadSurveyControllerTest,BranchScopeFilterTest,RegionApiControllerTest,RegionReviewControllerTest,DatasetPermissionServiceTest' test
python -X utf8 -m unittest discover -s scripts -p 'test_vroadai_image_links.py'
cd frontend
npm test -- --run
npm run build
$env:QA_CHROME_PATH='C:\Program Files\Google\Chrome\Application\chrome.exe'
Get-Content ..\.env | ForEach-Object { if ($_ -match '^(DEMO_ADMIN_PASSWORD|DEMO_MANAGER_PASSWORD)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') } }
npx playwright test e2e/vroad-local.spec.ts e2e/kcht-e2e.spec.ts
```
Các bài Playwright VroadAI đọc ảnh và hồ sơ từ API cục bộ; bài kiểm tra nút xác nhận xử lý giả lập riêng request POST nên chạy lặp không tạo hồ sơ xử lý giả cho hư hỏng thật. Kiểm thử ghi DB thực trước đó đã thực hiện riêng và ghi tại `docs/status.md`. Không dùng DB demo làm database cho toàn bộ Maven integration suite.

### Kiểm thử và Build Frontend:
```bash
cd frontend
# Kiểm tra Typecheck và Build Production
npm run build

# Định dạng mã nguồn (Format)
npm run format

# Kiểm tra quy chuẩn mã nguồn (Lint)
npm run lint
```

### Quy trình Tự động hóa CI (GitHub Actions):
Quy trình CI được cấu hình tự động tại `.github/workflows/ci.yml`, kích hoạt mỗi khi có `push` hoặc `pull_request` lên nhánh `main`, `master`, `develop`:
1. **Backend Job:** Cài đặt JDK 21 (Temurin), chạy toàn bộ unit & integration tests qua `./mvnw test`.
2. **Frontend Job:** Cài đặt Node.js 20, kiểm tra typecheck và build bundle Vite.

---

## 6. QUY TẮC KIẾN TRÚC VÀ TÀI LIỆU NGUỒN SỰ THẬT

- [`AI_RULES.md`](file:///c:/Demo_CucDuongBo/AI_RULES.md): Quy tắc coding, bảo mật, không làm mất dữ liệu, commit và review.
- [`AI_WORKFLOW.md`](file:///c:/Demo_CucDuongBo/AI_WORKFLOW.md): Quy trình thực thi theo task và lộ trình 15 giai đoạn.
- [`ARCHITECTURE.md`](file:///c:/Demo_CucDuongBo/ARCHITECTURE.md): Kiến trúc tổng thể, 14 đường lui và 6 quyết định bắt buộc.
- [`DATA_CONTRACT.md`](file:///c:/Demo_CucDuongBo/DATA_CONTRACT.md): Hợp đồng dữ liệu và quy chuẩn bàn giao Database sang Code.
- [`API_CONVENTIONS.md`](file:///c:/Demo_CucDuongBo/API_CONVENTIONS.md): Quy chuẩn REST API và cấu trúc phản hồi lỗi RFC 7807.
- [`DEFINITION_OF_DONE.md`](file:///c:/Demo_CucDuongBo/DEFINITION_OF_DONE.md): Checklist định nghĩa hoàn thành task.
- [`docs/architecture/MODULE_DEPENDENCY_RULES.md`](file:///c:/Demo_CucDuongBo/docs/architecture/MODULE_DEPENDENCY_RULES.md): Ma trận phụ thuộc được phép giữa 7 module nghiệp vụ.
- [`docs/reference/site-inventory.md`](file:///c:/Demo_CucDuongBo/docs/reference/site-inventory.md): Bảng tổng mục tính năng và phân kỳ MVP.
- [`docs/reference/route-map.md`](file:///c:/Demo_CucDuongBo/docs/reference/route-map.md): Bản đồ điều hướng và tuyến đường dẫn.
- [`docs/reference/dataset-map.md`](file:///c:/Demo_CucDuongBo/docs/reference/dataset-map.md): Ánh xạ 658 tập dữ liệu KCHT.
- [`docs/reference/role-permission-matrix.md`](file:///c:/Demo_CucDuongBo/docs/reference/role-permission-matrix.md): Ma trận phân quyền 4 vai trò (RBAC).
- [`docs/reference/ui-state-matrix.md`](file:///c:/Demo_CucDuongBo/docs/reference/ui-state-matrix.md): Ma trận trạng thái giao diện UI (Loading, Empty, Error).
- [`docs/reference/open-questions.md`](file:///c:/Demo_CucDuongBo/docs/reference/open-questions.md): Danh mục câu hỏi mở cần chốt.
