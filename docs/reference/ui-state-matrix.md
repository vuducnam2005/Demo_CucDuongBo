# MA TRẬN TRẠNG THÁI GIAO DIỆN NGƯỜI DÙNG (UI_STATE_MATRIX)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0 (Giai đoạn 0 - Khảo sát và Chốt phạm vi)

---

## 1. NGUYÊN TẮC THIẾT KẾ TRẢI NGHIỆM GIAO DIỆN (UI/UX PRINCIPLES)

1. **Không Bao giờ Để Màn hình Trắng (No Blank Screens):** Trong bất kỳ tình huống nào (đang tải, không có dữ liệu, mất mạng, lỗi máy chủ), giao diện luôn phải phản hồi một trạng thái trực quan rõ ràng kèm hướng dẫn hành động tiếp theo cho người dùng.
2. **Trải nghiệm Tải Tự nhiên (Skeleton First):** Ưu tiên sử dụng khung xương nạp (Skeleton Screens / Shimmer) mô phỏng chính xác cấu trúc bảng và thẻ thay vì dùng vòng quay (Spinner) chặn đứng toàn màn hình.
3. **Phản hồi Thao tác Tức thì (Optimistic UI & Instant Feedback):** Các thao tác cập nhật, lưu dữ liệu phải hiển thị trạng thái đang xử lý trên nút bấm (Loading Button) và xuất hiện thông báo (Toast/Notification) khi hoàn tất.
4. **Bảo vệ Trạng thái Dữ liệu:** Khi gặp lỗi hệ thống, không xóa trắng các form người dùng đang nhập dở; giữ nguyên dữ liệu trên bộ nhớ tạm để người dùng có thể thử lại.

---

## 2. MA TRẬN TRẠNG THÁI THEO TỪNG MÀN HÌNH CHỨC NĂNG

| Màn hình / Component | Trạng thái Bình thường (Normal State) | Trạng thái Đang tải (Loading State) | Trạng thái Rỗng (Empty State) | Trạng thái Lỗi (Error State) | Trạng thái Thoái hóa (Degraded State) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Bảng Điều hành (`DashboardPage`)** | Hiển thị đầy đủ các thẻ KPI, số liệu và biểu đồ phân bổ tài sản. | Hiển thị 4 thẻ Skeleton card màu xám nhấp nháy; biểu đồ hiển thị vòng tròn xoay mờ. | Hiển thị thẻ chỉ số mang giá trị `0` kèm nhãn *"Chưa phát sinh số liệu trong kỳ"*. | Hiển thị thẻ cảnh báo lỗi màu đỏ kèm nút *"Tải lại số liệu"*; không vỡ bố cục trang. | Khi API biểu đồ lỗi nhưng API thẻ KPI thành công: Vẫn hiển thị các thẻ KPI bình thường. |
| **Cây Danh mục Tài sản (`AssetTree`)** | Hiển thị cây phân cấp 57 loại tài sản kèm badge số lượng bản ghi. | Hiển thị thanh dọc Skeleton dạng danh sách cây 5 tầng. | Hiển thị thông báo *"Không tìm thấy loại tài sản phù hợp với từ khóa tìm kiếm"*. | Hiển thị icon cảnh báo mạng kèm nút *"Thử lại"* ngay tại chân cây danh mục. | Nếu mất kết nối tạm thời: Dùng cache TanStack Query hiển thị cây danh mục trước đó. |
| **Bảng Dữ liệu Động (`AssetGrid`)** | Hiển thị danh sách bản ghi, phân trang `page/size`, bộ lọc đa tiêu chí. | Hiển thị Table Skeleton gồm 10 hàng giả lập và các ô shimmer. | Hiển thị hình minh họa "Không có dữ liệu" kèm nút *"Xóa bộ lọc"* để quay lại toàn bộ danh sách. | Thông báo lỗi truy vấn kèm mã lỗi `KCHT-DB-xxx`, giữ nguyên thanh công cụ lọc để người dùng đổi tham số. | Nếu trường JSONB lỗi parsing: Hiển thị giá trị fallback `[Dữ liệu thô]` thay vì làm sập trang. |
| **Chi tiết Công trình (`AssetDetail`)** | Hiển thị 4 tab: Thông tin chung, Thuộc tính kỹ thuật, Hồ sơ tài liệu, Bản đồ vị trí. | Hiển thị Skeleton form gồm các nhãn và ô input xám mờ. | Trường hợp công trình đã bị xóa mềm: Hiển thị thông báo *"Công trình không tồn tại hoặc đã được chuyển vào lưu trữ"*. | Lỗi 404: Hiển thị trang 404 chuyên biệt kèm nút *"Quay lại danh sách"*. Lỗi 403: Báo *"Bạn không có quyền xem công trình này"*. | Nếu dịch vụ bản đồ lỗi: 3 tab thông tin văn bản vẫn xem bình thường; tab bản đồ hiện thông báo gián đoạn. |
| **Bản đồ Số WebGIS (`WebGisPage`)** | Hiển thị canvas bản đồ mượt mà, các lớp chuyên đề Point/Line, popup thông tin. | Hiển thị thanh tiến độ mỏng (Progress bar) ở mép trên bản đồ khi đang tải GeoJSON theo BBOX. | Khi phóng to/thu nhỏ vào vùng biển hoặc ngoài lãnh thổ: Khung nhìn trống, không hiển thị lỗi. | Nếu server GIS quá tải: Hiển thị thông báo Toast góc phải *"Không thể tải lớp biển báo. Vui lòng thử lại"*. | Nếu lớp Vector Tile bị chậm: Giữ nguyên lớp bản đồ nền và hiển thị icon cảnh báo lớp đang tải dở. |
| **Hồ sơ & Tài liệu (`DocumentExplorer`)**| Cây thư mục 121 folder bên trái, danh sách tệp đính kèm bên phải. | Hiển thị Skeleton cây thư mục và danh sách file dạng lưới (Grid). | Thư mục rỗng: Hiển thị *"Thư mục này chưa có tài liệu nào được đính kèm"*. | Lỗi tải metadata: Thông báo lỗi và cho phép tải lại. | Nếu dịch vụ MinIO tạm ngưng: Danh sách file vẫn hiển thị; nút "Tải xuống" tạm khóa kèm tooltip cảnh báo. |
| **Báo cáo Thống kê (`ReportPage`)** | Bảng tổng hợp số liệu chiều dài, bảo trì kèm nút *"Xuất Excel / PDF"*. | Nút xuất dữ liệu chuyển sang trạng thái Loading xoay tròn; bảng báo cáo shimmer. | Bộ lọc theo tỉnh/tuyến không có số liệu: Bảng hiển thị 1 hàng duy nhất *"Không phát sinh dữ liệu"*. | Báo cáo bị timeout (> 5s): Báo lỗi *"Truy vấn quá thời gian cho phép. Vui lòng thu hẹp bộ lọc thời gian/địa bàn"*. | Nếu dữ liệu lớn: Hệ thống đề xuất chuyển sang chế độ *"Xuất báo cáo ngầm trong nền"*. |
| **Quản trị Nạp Dữ liệu (`ImportMonitor`)**| Thanh tiến độ % động, bảng trạng thái 658 file, thống kê Read/Insert/Skip. | Icon xoay tròn trên hàng file đang xử lý; thanh tiến độ cập nhật mỗi 5 giây. | Không có phiên nạp nào đang chạy: Hiển thị *"Hệ thống đang ở trạng thái nhàn rỗi"*. | File bị lỗi cú pháp: Hàng dữ liệu chuyển sang màu đỏ kèm nút *"Xem nhật ký lỗi chi tiết"*. | Nếu Worker bị gián đoạn: Hiển thị trạng thái `SUSPENDED (Có thể Resume)` kèm nút kích hoạt tiếp tục. |

---

## 3. THIẾT KẾ CÁC THÔNG BÁO VÀ HỘP THOẠI HÀNH ĐỘNG (FEEDBACK STATES)

### 3.1 Thông báo Nổi (Toast Notifications)
- **Thành công (Success):** Màu xanh lá (`#52c41a`), tự động tắt sau 3 giây.
  - *Ví dụ:* "Cập nhật thông tin cầu Kỳ Lừa thành công."
- **Cảnh báo (Warning):** Màu vàng cam (`#faad14`), tự động tắt sau 4.5 giây.
  - *Ví dụ:* "Tọa độ nhập vào nằm gần biên giới tỉnh. Vui lòng kiểm tra lại."
- **Lỗi (Error):** Màu đỏ (`#ff4d4f`), giữ nguyên cho đến khi người dùng chủ động tắt hoặc sau 6 giây.
  - *Ví dụ:* "Không thể kết nối đến máy chủ lưu trữ tài liệu MinIO (Mã lỗi: 503)."

### 3.2 Hộp thoại Xác nhận Trước Thao tác Nguy hiểm (Confirmation Modals)
Mọi thao tác mang tính hủy hoại hoặc xóa mềm dữ liệu bắt buộc phải bật Modal xác nhận 2 bước:
- **Tiêu đề:** *"Xác nhận xóa công trình kết cấu hạ tầng"*
- **Nội dung:** *"Bạn có chắc chắn muốn xóa công trình [Tên công trình] (Mã: [record_key]) không? Dữ liệu sẽ được chuyển vào trạng thái lưu trữ và ghi nhận vào nhật ký kiểm toán."*
- **Nút hành động:** Nút "Hủy bỏ" (màu xám) và nút "Xác nhận xóa" (màu đỏ nguy hiểm, có đếm lùi 2 giây trước khi cho phép bấm).

---

## 4. QUY CHUẨN THIẾT KẾ ĐÁP ỨNG THIẾT BỊ (RESPONSIVE BEHAVIOR)

Hệ thống được tối ưu hóa cho các độ phân giải màn hình làm việc phổ biến của cơ quan hành chính nhà nước:

1. **Màn hình Máy tính Bàn Tiêu chuẩn (Desktop 1920x1080 & 1440x900):**
   - Hiển thị đầy đủ Sidebar mở rộng (260px width), Table dữ liệu 10-15 cột, không bị tràn màn hình.
2. **Màn hình Máy tính Xách tay (Laptop 1366x768 & 1280x800):**
   - Sidebar tự động chuyển sang chế độ thu gọn (Mini Sidebar 80px - chỉ hiện Icon).
   - Bảng dữ liệu tự động kích hoạt thanh cuộn ngang (Horizontal Scrollbar) với các cột hành động và tên công trình được cố định (Sticky Columns).
3. **Màn hình Máy tính Bảng Công tác Hiện trường (Tablet 1024x768):**
   - Sidebar chuyển thành Drawer ẩn, mở ra khi chạm vào Menu Hamburger.
   - Bản đồ WebGIS hỗ trợ thao tác cảm ứng chạm 2 ngón (Pinch-to-zoom) và xoay bản đồ.
