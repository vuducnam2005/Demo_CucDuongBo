# PROMPT 02 - Đồng bộ hệ thống KCHT giống web mẫu nhất có thể

Bạn là kỹ sư frontend/full-stack senior chịu trách nhiệm trực tiếp cải tiến mã nguồn tại:

`C:\Demo_CucDuongBo`

Website tham chiếu:

`https://kcht.drvn.gov.vn/dashboard`

## Điều kiện bắt đầu

Chỉ thực hiện prompt này sau khi `PROMPT_01_FIX_TOAN_BO_LOI.md` đã hoàn thành, toàn bộ lỗi P0 đã được sửa và các test liên quan đã đạt. Nếu phát hiện lỗi chức năng hoặc dữ liệu mới trong quá trình đồng bộ giao diện, sửa nguyên nhân gốc và bổ sung test trước khi tiếp tục.

Hãy sửa mã thật, chạy ứng dụng, so sánh trực tiếp và tự debug. Không chỉ viết kế hoạch hoặc tạo mockup. Mục tiêu là đạt mức tương đồng cao nhất có thể về cấu trúc, luồng điều hướng, sidebar, header, dashboard, bảng, biểu mẫu và trải nghiệm responsive, trong giới hạn dữ liệu/API/quyền truy cập hợp lệ.

## Nguyên tắc sử dụng website tham chiếu

1. Chỉ quan sát những phần tài khoản hiện tại được phép truy cập. Nếu cần đăng nhập, người dùng tự đăng nhập hoặc cung cấp thông tin theo kênh riêng; không lưu credential trong source, prompt, ảnh, log hoặc test.
2. Không bypass CAPTCHA, phân quyền hoặc bảo mật. Nếu không truy cập được một màn hình mẫu, dùng ảnh tham chiếu/tài liệu hiện có và ghi rõ giới hạn, không tự bịa hành vi.
3. Không sao chép trái phép logo, ảnh, font, bundle JS/CSS hoặc tài sản độc quyền. Hãy tái tạo cấu trúc và hành vi bằng component và asset thuộc dự án hoặc được phép sử dụng.
4. Không dùng ảnh chụp web mẫu làm nền để giả giao diện.
5. Không thay đổi database/backend chỉ để đạt hình thức. Tận dụng schema, API và dữ liệu thật hiện có; chỉ bổ sung API khi một tính năng thật sự cần dữ liệu mà contract hiện tại chưa cung cấp.
6. Không tạo menu trang trí, link chết, route trắng, nút giả hoặc số liệu hard-code.

## Giai đoạn 0 - Lập bộ đối chiếu trước khi sửa

Đọc `AI_RULES.md`, `AI_WORKFLOW.md`, `ARCHITECTURE.md`, `API_CONVENTIONS.md`, `DATA_CONTRACT.md`, `DEFINITION_OF_DONE.md`, `TEST_MATRIX.md`, các tài liệu `docs/reference`, source frontend và OpenAPI/backend controller.

Quan sát web mẫu và local tại cùng viewport. Tạo hoặc cập nhật:

- `docs/qa/SITE_PARITY_INVENTORY.md`
- `docs/qa/SIDEBAR_ROUTE_MATRIX.md`
- `docs/qa/VISUAL_PARITY_MATRIX.md`
- `docs/qa/PARITY_ACCEPTANCE_REPORT.md`

### `SITE_PARITY_INVENTORY.md`

Ghi cho từng màn hình mẫu:

- URL/route;
- quyền cần thiết;
- header, sidebar, breadcrumb và vùng nội dung;
- card, chart, table, filter, form, modal/drawer;
- request API quan sát được nếu được phép;
- trạng thái loading/empty/error;
- hành vi desktop/tablet/mobile;
- ảnh tham chiếu và viewport;
- phần chưa thể quan sát cùng lý do.

### `SIDEBAR_ROUTE_MATRIX.md`

Mỗi hàng phải có:

- thứ tự;
- nhãn menu mẫu;
- icon;
- menu cha/con;
- route mẫu;
- route local;
- component local;
- API/dataset dùng;
- quyền;
- trạng thái `đã có/cần sửa/cần tạo/không đủ dữ liệu`;
- tiêu chí nghiệm thu.

### `VISUAL_PARITY_MATRIX.md`

Mỗi hàng phải có route, viewport, vùng giao diện, web mẫu, local trước sửa, khác biệt, file/component cần sửa, local sau sửa và trạng thái đạt/chưa đạt.

Không bắt đầu đổi CSS hàng loạt trước khi ba ma trận đầu tiên được lập xong.

## Cấu trúc sidebar bắt buộc

Qua ảnh tham chiếu đã có, web mẫu hiển thị các mục theo thứ tự sau:

1. `Bảng tin`
2. `Cây tài sản`
3. `Đường quốc lộ`
4. `Đường cao tốc`
5. `Đường địa phương`
6. `Cầu`
7. `Hầm`
8. `An toàn giao thông`
9. `Thông tin giấy phép`
10. `Thống kê tài sản`
11. `Báo cáo`
12. `Bản đồ`
13. `Kế hoạch năm`
14. `Quản lý tài liệu`
15. `Tài liệu HDSD`

Local hiện mới có các mục tổng quát như `Bảng điều hành`, `Danh mục tài sản`, `Bản đồ số WebGIS`, `Báo cáo & Thống kê`, `Hồ sơ tài liệu`, `Danh mục chuẩn` và nhóm quản trị. Đây là khác biệt cấu trúc, không thể giải quyết chỉ bằng đổi màu.

Yêu cầu:

- Tái tạo đầy đủ thứ tự, phân cấp, icon, trạng thái active/hover/focus, expanded/collapsed và tooltip giống mẫu nhất có thể.
- Mỗi mục phải dẫn tới route/page thật. Có thể tái sử dụng component chung và truyền `assetType`, dataset hoặc filter qua route config; không nhân bản vô ích.
- Giữ các chức năng quản trị hiện có nhưng đặt chúng vào nhóm phù hợp theo role, không làm mất `/admin/users` và `/admin/audit-logs`.
- Route cũ phải được giữ làm alias/redirect hợp lý để bookmark hiện tại không hỏng.
- Selected key và open keys phải đúng khi refresh hoặc truy cập URL trực tiếp.
- Sidebar phải cuộn độc lập khi chiều cao thấp, không che footer/nội dung và dùng được khi collapsed.
- Trên mobile, sidebar chuyển thành Drawer hoặc cơ chế tương đương, có focus trap, đóng bằng Escape/chọn route và không khóa scroll sai.

Nếu một menu mẫu chưa có API/dataset tương ứng:

- tìm trong database metadata và API hiện có trước;
- nếu dữ liệu có nhưng chưa có endpoint, bổ sung endpoint theo convention của dự án và có test;
- nếu thật sự không có dữ liệu hoặc không có quyền tham chiếu, tạo page trạng thái rõ ràng và ghi vào parity report; không bịa dữ liệu, không ghi “đã hoàn thành”.

## Header và khung trang

Web mẫu có banner/header xanh toàn chiều rộng, tiêu đề hệ thống ở giữa, điều khiển menu/toàn màn hình bên trái và cụm ngôn ngữ/thông báo/tài khoản bên phải. Local hiện có header trắng và logo nằm trong sidebar.

Hãy tái cấu trúc `MainLayout` theo mẫu:

- header xanh chạy toàn chiều rộng phía trên sidebar và content;
- tiêu đề hệ thống cân bằng thị giác ở giữa, không bị cụm nút hai bên đẩy lệch;
- nút hamburger điều khiển sidebar và nút toàn màn hình có trạng thái/tooltip đúng;
- cụm ngôn ngữ, thông báo và tài khoản theo bố cục mẫu;
- thông báo phải lấy từ API thật hoặc hiển thị trạng thái chưa có dữ liệu, không dùng danh sách mock hiện có trong `MainLayout.tsx`;
- menu tài khoản có thông tin người dùng, role, đổi mật khẩu/thông tin nếu API hỗ trợ và đăng xuất thật;
- breadcrumb gọn, đúng route và không lặp `Trang chủ > Bảng tin` sai ngữ cảnh;
- content không bị che khi sidebar collapsed hoặc ở breakpoint nhỏ;
- loại bỏ inline style trùng lặp, chuyển sang token/component style có tổ chức.

## Dashboard/Bảng tin

Dashboard mẫu có cấu trúc tổng quan ưu tiên dữ liệu hạ tầng, gồm các KPI đường quốc lộ, đường cao tốc, đường địa phương, cầu, hầm và biểu đồ tình trạng cầu.

Yêu cầu:

- Đổi cấu trúc local sang hierarchy tương ứng: breadcrumb/tiêu đề ngắn, hàng KPI chính, biểu đồ và khu vực chi tiết.
- KPI lấy từ API/database thật và hiển thị đơn vị rõ ràng; không hard-code số trong ảnh mẫu.
- Card phải có icon, màu, border/shadow, khoảng cách, typography và độ cao gần mẫu ở cùng viewport.
- Biểu đồ tình trạng cầu phải dùng dữ liệu thật, có legend, tooltip, empty state và accessible label.
- Nếu API dashboard chưa tách đúng nhóm quốc lộ/cao tốc/địa phương/cầu/hầm, bổ sung aggregate query/API có test SQL; không tính trên một trang dữ liệu đã phân trang.
- Loại bỏ banner/khối local không tồn tại ở mẫu nếu nó không mang giá trị nghiệp vụ bắt buộc. Nếu bắt buộc giữ, đặt ở vị trí không phá cấu trúc chính và ghi lý do trong parity report.
- Kiểm tra format số `vi-VN`, loading skeleton, lỗi một widget không làm hỏng toàn dashboard và refresh dữ liệu.

## Các trang nghiệp vụ theo menu

Mỗi mục sidebar phải có màn hình dùng được, tối thiểu gồm:

- tiêu đề/breadcrumb khớp;
- bộ lọc phù hợp;
- bảng/card/bản đồ dùng dữ liệu thật;
- phân trang server-side;
- loading, empty, error và retry;
- chi tiết record bằng route hoặc drawer/modal;
- quyền thao tác rõ ràng;
- responsive không mất chức năng.

Ưu tiên tái sử dụng một `AssetListPage`/`AssetTable` có cấu hình metadata cho các nhóm đường, cầu, hầm, an toàn giao thông và giấy phép. Mỗi route phải áp đúng dataset/filter, không tải rồi lọc toàn bộ ở browser.

Các trang báo cáo, thống kê và bản đồ phải giữ chức năng đã sửa ở PROMPT 01. Không được hy sinh tính đúng dữ liệu để đổi giao diện.

## Hệ thống thiết kế

Đo trực tiếp từ ảnh/web mẫu và xây token dùng chung cho:

- màu header/sidebar/active/hover;
- màu nền content và card;
- font family có hỗ trợ đầy đủ tiếng Việt;
- font size/weight/line-height;
- spacing, border radius, border và shadow;
- chiều cao header, chiều rộng sidebar, khoảng cách content;
- button, input, select, table, tag, modal, drawer, tooltip và toast.

Ưu tiên tùy biến Ant Design theme và class có tên rõ ràng. Không rải hàng trăm inline style hoặc dùng `!important` tràn lan. Dùng CSS variables/design tokens để thay đổi nhất quán.

## Responsive và khả năng truy cập

Kiểm tra ít nhất các viewport:

- `1440 x 900`
- `1366 x 768`
- `1024 x 768`
- `768 x 1024`
- `390 x 844`

Yêu cầu:

- không có horizontal overflow toàn trang;
- bảng rộng cuộn trong container, không đẩy layout;
- header không chồng chữ/nút;
- sidebar/drawer dùng được bằng bàn phím;
- focus visible, contrast hợp lý, icon có accessible name;
- zoom 200% vẫn thực hiện được các thao tác chính;
- touch target trên mobile đủ lớn;
- chart có mô tả hoặc bảng dữ liệu thay thế.

## Kiểm thử đối chiếu hình ảnh

Tạo Playwright test/screenshot cho các route chính sau khi đăng nhập bằng fixture an toàn hoặc trạng thái xác thực dành cho test:

- dashboard/bảng tin;
- cây tài sản;
- quốc lộ, cao tốc, đường địa phương;
- cầu và hầm;
- an toàn giao thông/giấy phép;
- thống kê, báo cáo, bản đồ;
- kế hoạch năm, quản lý tài liệu và tài liệu HDSD;
- quản trị người dùng với role phù hợp.

Yêu cầu:

- chụp local ở cùng viewport với ảnh mẫu;
- lưu baseline của local trong cấu trúc test của dự án;
- mask dữ liệu thay đổi theo thời gian nếu cần nhưng không mask lỗi bố cục;
- kiểm tra sidebar mở/đóng, active item, menu con, header và khu vực nội dung;
- thiết lập ngưỡng visual diff có lý do, không đặt ngưỡng quá rộng để test luôn pass;
- khi không thể tự động chụp web mẫu do đăng nhập, dùng ảnh tham chiếu hợp lệ làm tài liệu đối chiếu thủ công và ghi rõ trong report.

## Hiệu năng và tính ổn định

- Không tải tất cả record để dựng dashboard hoặc bảng.
- Lazy-load route/module lớn khi hợp lý.
- Giữ bundle và số request ở mức hợp lý; không cài thư viện mới nếu Ant Design/OpenLayers hiện có đáp ứng được.
- Tránh render lại toàn layout khi một widget cập nhật.
- Hủy request cũ khi chuyển route/filter.
- Không làm hỏng refresh token, RBAC, WebGIS hoặc cache query đã sửa ở PROMPT 01.

## Thứ tự thực hiện

1. Chạy regression và xác nhận PROMPT 01 đạt.
2. Lập inventory, sidebar route matrix và visual parity matrix.
3. Chuẩn hóa route config và bổ sung các page/route/menu còn thiếu bằng dữ liệu thật.
4. Tái cấu trúc header, sidebar và responsive shell.
5. Tái cấu trúc dashboard theo mẫu.
6. Đồng bộ asset pages, reports, map, documents và các trang nghiệp vụ.
7. Chuẩn hóa design tokens và xử lý responsive/accessibility.
8. Thêm visual regression/E2E, so sánh cùng viewport và sửa sai lệch.
9. Hoàn thiện `PARITY_ACCEPTANCE_REPORT.md` và chạy toàn bộ kiểm thử.

## Lệnh xác minh tối thiểu

```powershell
cd C:\Demo_CucDuongBo
.\mvnw.bat test

cd C:\Demo_CucDuongBo\frontend
npm run lint
npm run test
npm run build
npm run test:e2e
```

Ngoài test tự động, phải kiểm tra thủ công tất cả mục sidebar ở desktop, tablet và mobile. Không báo pass nếu route mở được nhưng dữ liệu, nút chính hoặc quyền không hoạt động.

## Điều kiện nghiệm thu

Chỉ được báo hoàn thành khi:

- sidebar có đủ các mục đã quan sát trên web mẫu, đúng thứ tự/phân cấp và mỗi mục có route thật;
- header có cấu trúc banner xanh, tiêu đề và cụm điều khiển gần mẫu ở cùng viewport;
- dashboard có KPI/biểu đồ/hierarchy tương ứng và dùng dữ liệu thật;
- không còn notification hoặc dữ liệu mock trong luồng production;
- route cũ vẫn hoạt động qua alias/redirect phù hợp;
- không có link chết, màn hình trắng, nút thành công giả hoặc page chỉ để trang trí;
- responsive đạt ở năm viewport yêu cầu;
- keyboard/focus/contrast và các luồng chính có thể sử dụng;
- dữ liệu, RBAC, WebGIS và báo cáo vẫn đúng sau thay đổi giao diện;
- backend test, frontend lint/test/build/E2E và visual regression liên quan đạt;
- mọi phần chưa thể giống mẫu vì thiếu quyền, ảnh hoặc dữ liệu được ghi cụ thể trong parity report, không che giấu.

## Báo cáo cuối cùng

Trả về:

1. Bảng phần trăm/đánh giá parity theo từng route và từng vùng header/sidebar/content.
2. Danh sách route/menu đã thêm hoặc sửa và API/dataset tương ứng.
3. Danh sách file đã thay đổi.
4. Ảnh local trước/sau ở các viewport chính.
5. Lệnh test đã chạy và kết quả.
6. Khác biệt còn lại so với mẫu, nguyên nhân và việc cần làm để đạt gần hơn.

Không dùng câu “giống hệt” nếu chưa có bằng chứng đối chiếu toàn bộ route, trạng thái và viewport. Hãy báo mức tương đồng thực tế, có căn cứ.
